#!/usr/bin/env python3
"""Push the local tree to GitHub through api.github.com.

这台机器到 github.com:443 时通时断（经常 60 秒超时），但 api.github.com 一直很稳，所以发版脚本
在 git push 失败后用这个把本地 HEAD 的内容整理成一个提交推到远端，并按需创建 tag。

做法不依赖本地与远端的历史是否同源：直接比较「远端 HEAD 的 tree」和「本地 HEAD 的 tree」，
只把差异文件打成 blob，建 tree、建 commit（父提交 = 远端 HEAD），再更新 ref。

用法: tools/api_push.py [version]
"""
import base64
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

VER = sys.argv[1] if len(sys.argv) > 1 else None
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOKEN_FILE = os.environ.get('GH_TOKEN_FILE', '/root/java-mod-dev/githubtoken.txt')
REPO = os.environ.get('GH_REPO', 'furina1013-love/crystal_multiplier')
TOKEN = open(TOKEN_FILE).read().strip()


def git(*args, binary=False):
    out = subprocess.run(['git', '-C', ROOT] + list(args), capture_output=True)
    return out.stdout if binary else out.stdout.decode('utf-8', 'replace')


def api(method, path, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request('https://api.github.com' + path, data=data, method=method)
    req.add_header('Authorization', 'Bearer ' + TOKEN)
    req.add_header('Accept', 'application/vnd.github+json')
    req.add_header('X-GitHub-Api-Version', '2022-11-28')
    req.add_header('User-Agent', 'crystal-multiplier-api-push')
    if data:
        req.add_header('Content-Type', 'application/json')
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            body = r.read().decode()
            return json.loads(body) if body.strip() else {}
    except urllib.error.HTTPError as e:
        return {'__error': e.code, '__body': e.read().decode()[:300]}


def iso(ts):
    return subprocess.run(['date', '-u', '-d', '@' + ts, '+%Y-%m-%dT%H:%M:%SZ'],
                          capture_output=True, text=True).stdout.strip()


def local_files():
    files = {}
    for line in git('ls-tree', '-r', 'HEAD').splitlines():
        meta, path = line.split(chr(9), 1)
        mode, kind, sha = meta.split()
        if kind == 'blob':
            files[path] = (mode, sha)
    return files


def main():
    ref = api('GET', '/repos/%s/git/ref/heads/main' % REPO)
    if '__error' in ref:
        raise SystemExit('读远端 main 失败: %s' % ref['__body'])
    remote_sha = ref['object']['sha']
    remote_commit = api('GET', '/repos/%s/git/commits/%s' % (REPO, remote_sha))
    base_tree = remote_commit['tree']['sha']
    remote_tree = api('GET', '/repos/%s/git/trees/%s?recursive=1' % (REPO, base_tree))
    remote = {}
    for entry in remote_tree.get('tree', []):
        if entry['type'] == 'blob':
            remote[entry['path']] = (entry.get('mode', '100644'), entry['sha'])

    local = local_files()
    entries = []
    for path, (mode, sha) in sorted(local.items()):
        if remote.get(path) == (mode, sha):
            continue
        content = git('show', 'HEAD:' + path, binary=True)
        blob = api('POST', '/repos/%s/git/blobs' % REPO,
                   {'content': base64.b64encode(content).decode(), 'encoding': 'base64'})
        if '__error' in blob:
            raise SystemExit('上传 blob 失败 %s: %s' % (path, blob['__body']))
        entries.append({'path': path, 'mode': mode, 'type': 'blob', 'sha': blob['sha']})
    for path in sorted(set(remote) - set(local)):
        entries.append({'path': path, 'mode': '100644', 'type': 'blob', 'sha': None})

    print('远端 main %s / 差异文件 %d 个' % (remote_sha[:8], len(entries)))
    if not entries:
        print('内容已一致，无需推送')
    else:
        tree = api('POST', '/repos/%s/git/trees' % REPO, {'base_tree': base_tree, 'tree': entries})
        if '__error' in tree:
            raise SystemExit('建 tree 失败: %s' % tree['__body'])
        meta = git('log', '-1', '--format=%an|%ae|%at|%cn|%ce|%ct', 'HEAD').strip().split('|')
        message = git('log', '-1', '--format=%B', 'HEAD').rstrip() + chr(10)
        commit = api('POST', '/repos/%s/git/commits' % REPO, {
            'message': message, 'tree': tree['sha'], 'parents': [remote_sha],
            'author': {'name': meta[0], 'email': meta[1], 'date': iso(meta[2])},
            'committer': {'name': meta[3], 'email': meta[4], 'date': iso(meta[5])},
        })
        if '__error' in commit:
            raise SystemExit('建 commit 失败: %s' % commit['__body'])
        upd = api('PATCH', '/repos/%s/git/refs/heads/main' % REPO, {'sha': commit['sha'], 'force': False})
        if '__error' in upd:
            raise SystemExit('更新 main 失败: %s' % upd['__body'])
        remote_sha = commit['sha']
        print('main -> %s' % remote_sha[:8])

    if VER:
        tag = 'v' + VER
        if '__error' not in api('GET', '/repos/%s/git/ref/tags/%s' % (REPO, tag)):
            print('tag %s 已存在' % tag)
        else:
            obj = api('POST', '/repos/%s/git/tags' % REPO,
                      {'tag': tag, 'message': VER, 'object': remote_sha, 'type': 'commit'})
            if '__error' in obj:
                raise SystemExit('建 tag 失败: %s' % obj['__body'])
            newref = api('POST', '/repos/%s/git/refs' % REPO, {'ref': 'refs/tags/' + tag, 'sha': obj['sha']})
            if '__error' in newref:
                raise SystemExit('建 tag ref 失败: %s' % newref['__body'])
            print('tag %s -> %s' % (tag, remote_sha[:8]))
    print('API_PUSH_OK')


if __name__ == '__main__':
    main()
