#!/usr/bin/env python3
"""为指定版本建 GitHub Release 并上传 dist 里的产物。

用法: tools/gh_release.py 1.7.0
环境: 读 /root/java-mod-dev/githubtoken.txt（可用 GH_TOKEN_FILE 覆盖），仓库用 GH_REPO 覆盖。
发布说明默认取 README.md 里 "- **<version>**" 开头的更新记录段落。
"""
import json
import os
import sys
import urllib.error
import urllib.request

VER = sys.argv[1] if len(sys.argv) > 1 else None
if not VER:
    raise SystemExit('用法: tools/gh_release.py <version>')

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOKEN_FILE = os.environ.get('GH_TOKEN_FILE', '/root/java-mod-dev/githubtoken.txt')
REPO = os.environ.get('GH_REPO', 'furina1013-love/crystal_multiplier')
DIST = os.path.join(ROOT, 'dist')
TAG = 'v' + VER

TOKEN = open(TOKEN_FILE).read().strip()


def changelog(version):
    path = os.path.join(ROOT, 'README.md')
    lines = open(path, encoding='utf-8').read().split('\n')
    out = []
    for i, line in enumerate(lines):
        if line.startswith('- **%s**' % version):
            out.append(line)
            for nxt in lines[i + 1:]:
                if nxt.startswith('  ') or nxt.startswith('\t'):
                    out.append(nxt.strip())
                else:
                    break
            break
    return '\n'.join(out)


def call(method, url, data=None, ctype='application/json'):
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header('Authorization', 'Bearer ' + TOKEN)
    req.add_header('Accept', 'application/vnd.github+json')
    req.add_header('X-GitHub-Api-Version', '2022-11-28')
    req.add_header('User-Agent', 'crystal-multiplier-release')
    if data is not None:
        req.add_header('Content-Type', ctype)
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as exc:
        return {'__error': exc.code, '__body': exc.read().decode()[:400]}


body = changelog(VER) or ('## ' + VER)
jar = 'crystal_multiplier-%s.jar' % VER
if os.path.exists(os.path.join(DIST, jar)):
    body += '\n\n### 下载\n`dist/%s`（SHA-256 见 `SHA256SUMS.txt`）' % jar

payload = json.dumps({'tag_name': TAG, 'target_commitish': 'main', 'name': VER,
                      'body': body, 'draft': False, 'prerelease': False}).encode('utf-8')
rel = call('POST', 'https://api.github.com/repos/%s/releases' % REPO, payload)
if '__error' in rel:
    raise SystemExit('建 Release 失败 %s: %s' % (rel['__error'], rel['__body']))
print('release', rel['html_url'])

base = rel['upload_url'].split('{')[0]
assets = [(jar, 'application/java-archive'), ('SHA256SUMS.txt', 'text/plain'), ('cover.png', 'image/png')]
for name, ctype in assets:
    path = os.path.join(DIST, name)
    if not os.path.exists(path):
        print('跳过不存在的', name)
        continue
    res = call('POST', '%s?name=%s' % (base, name), open(path, 'rb').read(), ctype)
    if '__error' in res:
        print('上传失败', name, res['__error'], res['__body'])
    else:
        print('uploaded', res['name'], res['size'], 'bytes')
