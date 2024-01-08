## Step 0 - Installs
- gcloud CLI on local machine - `curl https://sdk.cloud.google.com | zsh`
- `gcloud auth login` then `gcloud auth configure-docker {artifact-registry-zone.pkg.dev}`
- [docker](https://docs.docker.com/engine/install/ubuntu/) on remote server 


## Locally
```
source .env && 
docker build . -t djinn:test
docker-compose up
```

## Step 2 - Update
`git push` OR `gcloud `


## Step 3 - Deploying To new GCE server
Ensure you have installed docker on your server first.
- Docker container must be mapped to port 80/443 on server
- enable `Allow full access to all Cloud APIs` in IAM otherwise cant read from Artifact Rgistry even if you give it a custom role with access.
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