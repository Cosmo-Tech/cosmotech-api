# Mock server
Mock server is prism: https://github.com/stoplightio/prism

## Run server
run mock server with prism: 
`prism mock openapi.yaml`

## Mock query
### Example with curl
curl -H "Authorization: Bearer 123213" http://localhost:4010/users?apiKey=dsdqsdqs

curl -H 'Authorization: Bearer 123213' http://localhost:8080/connectors/upload?apiKey=dsdqsdqs -X POST --data-binary '@/home/vcarluer/dev/phoenix/cosmotech-api/openapi/example_files/ADTConnector.yaml' -H 'content-type: application/yaml'

