# Gate Proxy

Proxy HTTP/HTTPS em Java (Spring Boot + Netty) com suporte a:

- **reescrita de resposta** baseada em regras configuradas em CSV
- **reverse proxy por domínio** com upstream configurável em CSV

## Pré-requisitos

- Java 17
- OpenSSL (para gerar os certificados CA locais)

## Configuração básica

1. Copie o arquivo de exemplo:

```bash
cp src/main/resources/application.properties.exemple src/main/resources/application.properties
```

2. Edite `src/main/resources/application.properties` e ajuste o CSV de configuração das regras:

```properties
spring.datasource.url=jdbc:relique:csv:classpath:base
```

Esse valor aponta para a pasta onde está seu CSV (`src/main/resources/base/proxy.csv`).

Se quiser usar outro diretório/arquivo de regras, atualize a URL para o seu caminho de CSV.

3. Configure a porta e paths dos certificados (se necessário):

```properties
proxy.port=8080
proxy.ca.cert-path=classpath:ca/ca.pem
proxy.ca.key-path=classpath:ca/ca.key
```

4. Gere o CA local (caso ainda não exista):

```bash
openssl genrsa -out src/main/resources/ca/ca.key 2048
openssl req -x509 -new -nodes -key src/main/resources/ca/ca.key -sha256 -days 3650 -out src/main/resources/ca/ca.pem -subj "/CN=Gate Local CA"
```

## Como criar regras no CSV

Arquivo padrão: `src/main/resources/base/proxy.csv`

Cabeçalho esperado:

```csv
id,scheme,host,path,method,response,content,status,reverse,reverse_uri
```

### Regras de preenchimento

- `id`: inteiro único por regra.
- `scheme`: `http` ou `https`.
- `host`: domínio/host exato (ex.: `api.exemplo.com`).
- `path`: caminho exato da rota (ex.: `/v1/users`).
- `method`: um dos métodos suportados: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`.
- `response`: body fixo que será retornado quando a regra bater.
- `content`: tipo de conteúdo de resposta: `JSON`, `HTML` ou `XML`.
- `status`: status HTTP da resposta reescrita (ex.: `200`, `404`, `500`).
- `reverse`: habilita reverse proxy para o `host` (`true` ou `false`).
- `reverse_uri`: URI base do upstream quando `reverse=true` (ex.: `https://upstream.internal:8443`).

### Como o match funciona

O pipeline segue esta ordem:

1. Tenta regra de **reverse proxy** por `host` com `reverse=true`.
2. Se não houver match de reverse proxy, tenta regra de **reescrita** por match **exato** de:
- URL completa (`scheme + host + path`)
- método HTTP (`method`)
3. Se não houver match em nenhuma regra, a requisição segue para o upstream original.

No reverse proxy, `path`, `query` e `fragment` da requisição original são preservados, trocando apenas `scheme` e `authority` pelo `reverse_uri`.

### Exemplo de regras

```csv
id,scheme,host,path,method,response,content,status,reverse,reverse_uri
1,https,api.exemplo.com,/v1/users,GET,"{\"ok\":true,\"source\":\"gate\"}",JSON,200,false,
2,https,api.exemplo.com,/,GET,"",JSON,200,true,https://upstream.internal:8443
```

## Executando o projeto

```bash
./gradlew bootRun
```

## Features

- [x] Reescrita da resposta da requisição (via regras no CSV)
- [x] Reverse proxy por domínio
- [ ] Log do curl das requisições que passam pelo proxy
