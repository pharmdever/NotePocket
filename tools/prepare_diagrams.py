#!/usr/bin/env python3
"""Fetch one immutable, integrity-pinned offline renderer at build time only."""
import base64,hashlib,io,pathlib,sys,tarfile,urllib.request
VERSION='10.9.3'
INTEGRITY='V80X1isSEvAewIL3xhmz/rVmc27CVljcsbWxkxlWJWY/1kQa4XOABqpDl2qQLGKzpKm6WbTfUEKImBlUfFYArw=='
out=pathlib.Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
archive=out.parent.parent/('mermaid-'+VERSION+'.tgz')
if not archive.exists() or base64.b64encode(hashlib.sha512(archive.read_bytes()).digest()).decode()!=INTEGRITY:
    with urllib.request.urlopen('https://registry.npmjs.org/mermaid/-/mermaid-'+VERSION+'.tgz',timeout=90) as r:
        data=r.read(20*1024*1024+1)
    if len(data)>20*1024*1024 or base64.b64encode(hashlib.sha512(data).digest()).decode()!=INTEGRITY:
        raise RuntimeError('Mermaid archive integrity mismatch')
    archive.write_bytes(data)
with tarfile.open(fileobj=io.BytesIO(archive.read_bytes()),mode='r:gz') as tar:
    for source,target in [('package/dist/mermaid.min.js','mermaid.min.js'),('package/LICENSE','MERMAID-LICENSE.txt')]:
        member=tar.getmember(source)
        if member.size>12*1024*1024:raise RuntimeError('Unexpected renderer size')
        data=tar.extractfile(member).read()
        destination=out/target
        if not destination.exists() or destination.read_bytes()!=data:destination.write_bytes(data)
# The archive must not be packaged into the APK.
print('Verified offline Mermaid '+VERSION)
