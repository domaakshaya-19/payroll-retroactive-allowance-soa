from pathlib import Path
import json
def dependency(artifact,group='org.springframework.boot',version=None):return '<dependency><groupId>'+group+'</groupId><artifactId>'+artifact+'</artifactId>'+('<version>'+version+'</version>' if version else '')+'<scope>test</scope></dependency>'
for module in ['allowance-rule-service','retro-adjustment-service','contracts']:
    p=Path(module)/'pom.xml';s=p.read_text();deps=dependency('json-schema-validator','com.networknt','1.5.6')
    if module=='contracts':deps+=dependency('spring-boot-starter-test')+dependency('jackson-datatype-jsr310','com.fasterxml.jackson.datatype')
    s=s.replace('</dependencies>',deps+'</dependencies>');p.write_text(s)
p=Path('pom.xml');s=p.read_text().replace('</project>','''<build><pluginManagement><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><configuration><systemPropertyVariables><contract.break>${contract.break}</contract.break></systemPropertyVariables></configuration></plugin></plugins></pluginManagement></build>
  <profiles><profile><id>contract-negative</id><properties><contract.break>true</contract.break></properties></profile></profiles>
</project>''');p.write_text(s)
event={'eventId':'ebbe594b-59d3-4bf0-9c04-e97c466e69ca','aggregateId':'EMP001:HOUSING','schemaVersion':1,'occurredAt':'2026-10-25T00:00:00Z','correlationId':'f9c16f53-808a-449b-8b2b-a6df7385f557','eventType':'AllowanceRuleBackdated','employeeId':'EMP001','allowanceType':'HOUSING','oldVersion':1,'newVersion':2,'oldAmount':2000.00,'newAmount':3000.00,'effectiveFrom':'2026-08-01'}
snapshot={'statementId':1,'periodId':1,'employeeId':'EMP001','startsOn':'2026-08-01','endsOn':'2026-08-31','allowanceType':'HOUSING','ruleVersion':1,'originalAmount':2000.00,'issuedAt':'2026-08-31T00:00:00Z'}
schema={'$schema':'https://json-schema.org/draft/2020-12/schema','title':'HistoricalSnapshot v1','type':'object','additionalProperties':False,'required':list(snapshot),'properties':{}}
for name,value in snapshot.items():
    if name in ['statementId','periodId','ruleVersion']:prop={'type':'integer','minimum':1}
    elif name=='originalAmount':prop={'type':'number','minimum':0,'multipleOf':0.01}
    elif name in ['startsOn','endsOn']:prop={'type':'string','format':'date'}
    elif name=='issuedAt':prop={'type':'string','format':'date-time'}
    else:prop={'type':'string','minLength':1}
    schema['properties'][name]=prop
for path,data in [('contracts/src/main/resources/fixtures/allowance-rule-backdated-v1.json',event),('contracts/src/main/resources/fixtures/historical-snapshot-v1.json',snapshot),('contracts/src/main/resources/schemas/historical-snapshot-v1.schema.json',schema)]:
    p=Path(path);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,indent=2)+'\n')
