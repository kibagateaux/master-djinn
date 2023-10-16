## Mutations
```graphql
mutation submit_raw_data(
  $raw_data: [RawInputData],
  $source: DataProviders!,
  $name: String!
){
  submit_data(
    data: $raw_data,
    data_provider: $source,
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
        "clientRecordVersion": 0,
        "dataOrigin": "com.google.android.apps.fitness",
        "device": 0,
        "id": "079e8187-15f2-421d-8024-7c4b2f5fda06",
        "lastModifiedTime": "2023-09-07T09:57:52.715Z",
        "recordingMethod": 0
      }
  }],
  "source": "AndroidHealthConnect",
  "name":"Step"
}
```