## Mutations
```graphql
mutation submit_raw_data(
  $raw_data: [RawInputData],
  $provider: DataProviders!,
  $name: String!
){
  submit_data(
    data: $raw_data,
    data_provider: $provider,
    name: $name,
    player_id: "me"
  )
}


{
   "raw_data": [{
      "count": 9,
      "startTime": "2023-09-07T09:44:16.818Z",
      "endTime": "2023-09-07T09:45:16.819Z",
      "metadata": {
        "clientRecordId": null,
        "clientRecordVersion": "0",
        "dataOrigin": "com.google.android.apps.fitness",
        "device": "0",
        "id": "079e8187-15f2-421d-8024-7c4b2f5fda06",
        "lastModifiedTime": "2023-09-07T09:57:52.715Z",
        "recordingMethod": "0"
      }
  }],
  "provider": "AndroidHealthConnect",
  "name":"Step",
  "player_id": "0x0AdC54d8113237e452b614169469b99931cF094e"
}
```