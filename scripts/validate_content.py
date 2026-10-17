import json,pathlib,sys
from jsonschema import Draft202012Validator
def unique_object(pairs):
 result={}
 for key,value in pairs:
  if key in result: raise ValueError('Duplicate JSON key: '+key)
  result[key]=value
 return result
def load(path): return json.loads(path.read_text(encoding='utf-8'),object_pairs_hook=unique_object)
root=pathlib.Path(__file__).resolve().parents[1]/'content'
schema=load(root/'schemas/level.schema.json')
Draft202012Validator.check_schema(schema)
v=Draft202012Validator(schema); index=load(root/'index.json')
for file in index:
 data=load(root/file); errors=list(v.iter_errors(data))
 if errors:
  for e in errors: print(file, '/'.join(map(str,e.absolute_path)), e.message)
  sys.exit(1)
if '--require-60' in sys.argv and len(index)!=60: sys.exit('Incomplete product: expected 60 playable levels, found '+str(len(index)))
print('JSON Schema draft 2020-12: '+str(len(index))+' level documents validated.')
