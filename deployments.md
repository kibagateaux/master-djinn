## Step 0 - Installs
- gcloud CLI on local machine - `curl https://sdk.cloud.google.com | zsh`
- [docker](https://docs.docker.com/engine/install/ubuntu/) on remote server 

## Locally
```
source .env && 
docker build . -t djinn:test
docker-compose up
```

## Step 2 - Update
`git push` OR `gcloud `

## Step 3 - Deploying To new remote Server
Ensure you have installed docker on your server first.
- Docker container must be mapped to port 80/443 on server
- 
```
touch .env docker-compose.yml
echo "local env file contents" >> .env
echo "local docker-compose file contents" >> docker-compose.yml
echo alias docker-compose="'"'docker run --rm \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v "$PWD:$PWD" \
    -w="$PWD" \
    docker/compose'"'" >> ~/.bashrc

docker pull docker/compose

source ~/.bashrc

docker-compose up
```