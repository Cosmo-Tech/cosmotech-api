# Mock server
Mock server is prism: https://github.com/stoplightio/prism

## Run server
run mock server with prism: 
`prism mock openapi.yaml`

## Mock query
### Restish
#### Install
https://github.com/danielgtaylor/restish
get a release: 
```
wget https://github.com/danielgtaylor/restish/releases/download/v0.7.0/restish-0.7.0-linux-x86_64.tar.gz
tar xf restish-0.7.0-linux-x86_64.tar.gz
mv restish ~/.local/bin
```
#### Use


### Example with curl
curl -H "Authorization: Bearer 123213" http://localhost:4010/users?apiKey=dsdqsdqs
