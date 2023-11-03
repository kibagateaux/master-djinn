## Step 1 - Build
```
source .env && 
docker build . -t djinn:test
```

```
docker-compose up
```

## Step 2 - Update
`docker push kibagateaux/master-djinn:test-0-0-1`

## Step 3 - restart server
`ssh into cloud`
`docker run - port etc`