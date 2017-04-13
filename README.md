# GuiaMobilCouchbaseSyncGatewayRestAPI
Based on project from Couchbase labs. Adaptation to connect our android app and our data manager in Desktop Java and WebJava to the sync Gateway and have information from buckets sync seamlessly via the couchbase sync gateway rest api. Apparently current couchbase smart clients for Java cannot connect to Sync Gateway directly. Data persisted directly to couchbase servers buckets directly cannot be replicated to mobile via sync gateway. The Sync Gateway REST API is the way to go to have data sync bewteen couchbase server and couchbase lite. Thats the reason for this project.
Uses SpringBoot to produce UberJars.
Maven based project.
