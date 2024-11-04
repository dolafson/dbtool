# dbtool
command-line jdbc adaptor that provides a vendor agnostic front-end to multiple databases

build
- ```mvn clean install```


config
- ```vim ~/.dbtool/dbtool.properties```
  
```
jdbc.url.mydb1=jdbc:postgresql://HOST/DB
jdbc.class.mydb1=org.postgresql.Driver
jdbc.user.mydb1=...
jdbc.schema.mydb1=...
jdbc.password.mydb1=...

node=mydb1
human=true

# add other nodes as needed ...
# note, all config options can be overriden on the command line
```
  

for full usage - run with no arguments
- ```java -jar target/dbtool.jar```

simple query
- ```java -jar target/dbtool.jar "select count(*) from mytable"```
