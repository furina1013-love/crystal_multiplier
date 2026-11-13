#!/usr/bin/env python3
"""Push main + tag through api.github.com instead of git over HTTPS.

这台机器到 github.com:443 的连接时好时坏（经常 60s 超时），但 api.github.com 一直是通的，
所以发版脚本在 git push 失败后用这个把还没推的提交和 tag 用 API 建出来。

用法: tools/api_push.py <version>   # 会推 HEAD -> main，并创建 refs/tags/v<version>
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


def git(*args):
    return subprocess.run(['git', '-C', ROOT] + list(args), capture_output=True, text=True).stdout


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


def commit_meta(sha):
    out = git('log', '-1', '--format=%an|%ae|%at|%cn|%ce|%ct|%B', sha).split(chr(10), 1)
    head, body = out[0], (out[1] if len(out) > 1 else '')
    an, ae, at, cn, ce, ct = head.split('|')
    return ({'name': an, 'email': ae, 'date': iso(at)},
            {'name': cn, 'email': ce, 'date': iso(ct)}, body)


def build_tree(base_tree, sha):
    changes = [l for l in git('diff-tree', '--no-commit-id', '--name-status', '-r', sha).splitlines() if l.strip()]
    entries = []
    for line in changes:
        parts = line.split(chr(9))
        status, path = parts[0][0], parts[-1]
        if status == 'D':
            entries.append({'path': path, 'mode': '100644', 'type': 'blob', 'sha': None})
            continue
        content = subprocess.run(['git', '-C', ROOT, 'show', '%s:%s' % (sha, path)],
                                 capture_output=True).stdout
        blob = api('POST', '/repos/%s/git/blobs' % REPO,
                   {'content': base64.b64encode(content).decode(), 'encoding': 'base64'})
        if '__error' in blob:
            raise SystemExit('blob %s 失败 %s' % (path, blob['__body']))
        entries.append({'path': path, 'mode': '100644', 'type': 'blob', 'sha': blob['sha']})
    tree = api('POST', '/repos/%s/git/trees' % REPO, {'base_tree': base_tree, 'tree': entries})
    if '__error' in tree:
        raise SystemExit('tree 失败 %s' % tree['__body'])
    return tree['sha']


head = git('rev-parse', 'HEAD').strip()
ref = api('GET', '/repos/%s/git/ref/heads/main' % REPO)
if '__error' in ref:
    raise SystemExit('读远端 main 失败 %s' % ref['__body'])
remote = ref['object']['sha']
print('remote main', remote[:12], '-> local', head[:12])

if remote != head:
    pending = [c for c in git('rev-list', '--reverse', '%s..%s' % (remote, head)).split() if c]
    print('待推提交', len(pending))
    parent = remote
    base_tree = api('GET', '/repos/%s/git/commits/%s' % (REPO, remote))['tree']['sha']
    for sha in pending:
        author, committer, message = commit_meta(sha)
        tree = build_tree(base_tree, sha)
        commit = api('POST', '/repos/%s/git/commits' % REPO,
                     {'message': message, 'tree': tree, 'parents': [parent],
                      'author': author, 'committer': committer})
        if '__error' in commit:
            raise SystemExit('commit 失败 %s' % commit['__body'])
        print('  commit', sha[:8], '->', commit['sha'][:8])
        parent = commit['sha']
        base_tree = tree
    upd = api('PATCH', '/repos/%s/git/refs/heads/main' % REPO, {'sha': parent, 'force': False})
    if '__error' in upd:
        raise SystemExit('更新 main 失败 %s' % upd['__body'])
    print('main ->', upd['object']['sha'][:12])

if VER:
    tag = 'v' + VER
    local_commit = git('rev-parse', 'refs/tags/%s^{commit}' % tag).strip()
    if not local_commit:
        raise SystemExit('本地没有 tag %s' % tag)
    existing = api('GET', '/repos/%s/git/ref/tags/%s' % (REPO, tag))
    if '__error' not in existing:
        print('tag', tag, '已存在，跳过')
    else:
        obj = api('POST', '/repos/%s/git/tags' % REPO,
                  {'tag': tag, 'message': VER, 'object': local_commit, 'type': 'commit'})
        if '__error' in obj:
            raise SystemExit('建 tag 对象失败 %s' % obj['__body'])
        newref = api('POST', '/repos/%s/git/refs' % REPO,
                     {'ref': 'refs/tags/' + tag, 'sha': obj['sha']})
        if '__error' in newref:
            raise SystemExit('建 tag ref 失败 %s' % newref['__body'])
        print('tag', tag, '->', obj['sha'][:12])
print('API_PUSH_OK')
