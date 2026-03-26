# Gate Proxy

Proxy HTTP/HTTPS em Java (Spring Boot + Netty) com suporte a **reescrita de resposta** baseada em regras configuradas em CSV.

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
id,scheme,host,path,method,response,content
```

### Regras de preenchimento

- `id`: inteiro único por regra.
- `scheme`: `http` ou `https`.
- `host`: domínio/host exato (ex.: `api.exemplo.com`).
- `path`: caminho exato da rota (ex.: `/v1/users`).
- `method`: um dos métodos suportados: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`.
- `response`: body fixo que será retornado quando a regra bater.
- `content`: tipo de conteúdo de resposta: `JSON`, `HTML` ou `XML`.

### Como o match funciona

A regra só é aplicada quando houver match **exato** de:

- URL completa (`scheme + host + path`)
- método HTTP (`method`)

Se não houver match, a requisição segue para o upstream normalmente.

### Exemplo de regra

```csv
id,scheme,host,path,method,response,content
1,https,api.exemplo.com,/v1/users,GET,"{\"ok\":true,\"source\":\"gate\"}",JSON
```

## Executando o projeto

```bash
./gradlew bootRun
```

## Features

- [x] Reescrita da resposta da requisição (via regras no CSV)
- [ ] Reverse proxy por domínio
- [ ] Log do curl das requisições que passam pelo proxy
