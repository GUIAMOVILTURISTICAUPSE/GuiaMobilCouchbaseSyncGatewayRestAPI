package main.java;


import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.auth.Authenticator;
import com.couchbase.client.java.datastructures.collections.CouchbaseArrayList;
import com.couchbase.client.java.document.json.JsonArray;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@SpringBootApplication
@RestController
@RequestMapping("/api")
public class RestApiSyncGateway implements Filter { 

    public static void main(String[] args) {
        SpringApplication.run(RestApiSyncGateway.class, args);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        chain.doFilter(req, res);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void destroy() {}

    @Value("${server.hostname}")
    private String serverHostname;

    @Value("${server.bucket}")
    private String serverBucket;

    @Value("${server.password}")
    private String serverPassword;
    
    @Value("${bucket.password}")
    private String serverBucketPassword;

    @Value("${gateway.hostname}")
    private String gatewayHostname;
    
    @Value("${gateway.database}")
    private String gatewayDatabase;
    
    @Value("${server.username}")
    private String serverUsername;

    private CouchbaseEnvironment configurarCouchBaseEnvironment()
    {
    	CouchbaseEnvironment env = DefaultCouchbaseEnvironment.builder()
    		    //this set the IO socket timeout globally, to 45s
    		    .socketConnectTimeout((int) TimeUnit.SECONDS.toMillis(1000))
    		    //this sets the connection timeout for openBucket calls globally (unless a particular call provides its own timeout)
    		    .connectTimeout(TimeUnit.SECONDS.toMillis(1000))
    		    .build();
    	
    	
    	return env;
    }
    
    public @Bean
    Cluster cluster() {
    	//Authenticator auth = new com.couchbase.client.java.auth.PasswordAuthenticator(serverUsername, serverPassword);
    	Cluster cluster = CouchbaseCluster.create(configurarCouchBaseEnvironment(),serverHostname);
    			//cluster.authenticate(auth);
    			/*for(int i=0; i <1000; i++)
    			{
    				System.out.println("Autenticando");
    			}*/
        return cluster;
    }

    public @Bean
    Bucket bucket() {
    	System.out.println("Abriendo bucket: " + serverBucket + "en servidor: " + serverHostname);
    	Bucket b = cluster().openBucket(serverBucket);
    	System.out.println("Bucket: " + b.toString());
    	return b;
        //return cluster().openBucket(serverBucket, serverBucketPassword);
    }

    @RequestMapping(value="/recursos/{recursoId}", method= RequestMethod.GET)
    public Object getRecursoById(@PathVariable("recursoId") String todoId) {
        return DatabaseForRest.getById(bucket(), todoId);
    }

    @RequestMapping(value="/recursos", method= RequestMethod.GET)
    public Object getAllRecursos() {
    	System.out.println("Llamando al metodo de traer todos los recursos");
        List<Map<String, Object>> recursosEncontrados = DatabaseForRest.getAll(bucket());
        for(Map<String,Object> m:recursosEncontrados)
        	System.out.println(m.values());
        return recursosEncontrados;
    }

    @RequestMapping(value="/recursos", method= RequestMethod.DELETE)
    public Object deleteAllRecursos(@RequestBody String json) {
        JsonArray jsonData = JsonArray.fromJson(json);
        JsonArray responses = JsonArray.create();
        for(int i = 0; i < jsonData.size(); i++) {
            responses.add(makeDeleteRequest("http://" + gatewayHostname + ":4984/" + bucket().name() + "/" + jsonData.getObject(i).getString("id") + "?rev=" + jsonData.getObject(i).getString("rev")));
        }
        return new ResponseEntity<String>(responses.toString(), HttpStatus.OK);
    }


    @RequestMapping(value="/recursos", method= RequestMethod.POST)
    public Object createRecurso(@RequestBody String json) {
        JsonObject jsonData = JsonObject.fromJson(json);
        if(!jsonData.containsKey("descripcion")) {
            return new ResponseEntity<String>(JsonObject.create().put("error", 400).put("message", "El recurso debe tener una descripcion").toString(), HttpStatus.BAD_REQUEST);
        } else if(!jsonData.containsKey("posicion")) {
            return new ResponseEntity<String>(JsonObject.create().put("error", 400).put("message", "El recurso debe tener datos de geolocalizacion").toString(), HttpStatus.BAD_REQUEST);
        }
        JsonObject data = JsonObject.create().put("_id", jsonData.get("nombre")).put("descripcion", jsonData.get("descripcion")).put("documentClass", "class es.codigoandroid.pojos.Recursos").put("posicion", jsonData.get("posicion"));
        JsonObject response = makePostRequest("http://" + gatewayHostname + ":4984/" + gatewayDatabase + "/", data.toString());
        return new ResponseEntity<String>(response.getObject("content").toString(), HttpStatus.valueOf(response.getInt("status")));
    }
    
    @RequestMapping(value="/recursos", method= RequestMethod.PUT)
    public Object updateRecurso(@RequestBody String json) {
    	System.out.println("Entro a put request de recurso");
        JsonObject jsonData = JsonObject.fromJson(json);
        String rev;
        if(!jsonData.containsKey("_sync")) {
            return new ResponseEntity<String>(JsonObject.create().put("error", 400).put("message", "El recurso debe tener una revision para hacer put").toString(), HttpStatus.BAD_REQUEST);
        }else{
        	//rev = jsonData.getString("_sync");
        	JsonObject jo = jsonData.getObject("_sync");
        	rev = jo.getString("rev");
        	System.out.println("Rev: " + rev);
        }
        if(jsonData.containsKey("_sync")) {
        	jsonData.removeKey("_sync");
        }
        if(jsonData.containsKey("_attachments")) {
        	jsonData.removeKey("_attachments");
        }
        /*JsonObject data = JsonObject.create().put("_id", jsonData.get("_id")).put("descripcion", jsonData.get("descripcion")).put("documentClass", "class es.codigoandroid.pojos.Recursos")
        		.put("posicion", jsonData.get("posicion"))
        		.put("comentarios",jsonData.get("comentarios"))
        		.put("costoRecursos",jsonData.get("costoRecursos"))
        		.put("descripcion", jsonData.get("descripcion"))
        		.put("direccion", jsonData.get("direccion"))
        		.put("documentClass", jsonData.get("class es.codigoandroid.pojos.Recursos"))
        		.put("_attachments",jsonData.get("_attachments"));
        		 * 
        		 */
        
        JsonObject data = jsonData;
        String putRequest = "http://" + gatewayHostname + ":4984/" + gatewayDatabase + "/" + jsonData.get("_id") + "?rev="+rev;
        System.out.println("PutRequest: " + putRequest);
        System.out.println("Data: " + data.toString());
        JsonObject response = makePutRequest(putRequest, data.toString());
        return new ResponseEntity<String>(response.getObject("content").toString(), HttpStatus.valueOf(response.getInt("status")));
    }

    private JsonObject makePostRequest(String url, String body) {
        JsonObject jsonResult = JsonObject.create();
        try {
            HttpClient client = HttpClientBuilder.create().build();
            HttpPost post = new HttpPost(url);
            post.setEntity(new StringEntity(body, ContentType.create("application/json")));
            HttpResponse response = client.execute(post);
            BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            String line = "";
            String result = "";
            while ((line = rd.readLine()) != null) {
                result += line;
            }
            jsonResult.put("status", response.getStatusLine().getStatusCode());
            jsonResult.put("content", JsonObject.fromJson(result));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonResult;
    }
    
    private JsonObject makePutRequest(String url, String body) {
        JsonObject jsonResult = JsonObject.create();
        try {
            HttpClient client = HttpClientBuilder.create().build();
            HttpPut put = new HttpPut(url);
            put.setEntity(new StringEntity(body, ContentType.create("application/json")));
            HttpResponse response = client.execute(put);
            BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            String line = "";
            String result = "";
            while ((line = rd.readLine()) != null) {
                result += line;
            }
            jsonResult.put("status", response.getStatusLine().getStatusCode());
            jsonResult.put("content", JsonObject.fromJson(result));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonResult;
    }

    private JsonObject makeDeleteRequest(String url) {
        JsonObject jsonResult = JsonObject.create();
        try {
            HttpClient client = HttpClientBuilder.create().build();
            HttpDelete delete = new HttpDelete(url);
            HttpResponse response = client.execute(delete);
            BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            String line = "";
            String result = "";
            while ((line = rd.readLine()) != null) {
                result += line;
            }
            jsonResult.put("status", response.getStatusLine().getStatusCode());
            jsonResult.put("content", JsonObject.fromJson(result));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonResult;
    }

}
