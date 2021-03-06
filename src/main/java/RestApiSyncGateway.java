package main.java;


import com.couchbase.client.deps.com.fasterxml.jackson.core.JsonProcessingException;
import com.couchbase.client.deps.com.fasterxml.jackson.databind.ObjectMapper;
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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
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
import java.net.URI;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

//Con anotaciones para Byron

//Esta anotacion le dice a Spring Boot que desde aqui inicia el application (debe tener un main)
@SpringBootApplication

//Esta anotacion sirve para decirle a Srping que esta clase manejara servicios web REST.
@RestController

//Esta anotacion permite el mapeo a esta clase
@RequestMapping("/api")
public class RestApiSyncGateway implements Filter { 

	//Indispensable para poder correr los microservicios.
	//Spring boot levanta su propio tomcat. Este es el punto de inicio.
	//Le debes decir que esta clase es la que inicia la ejecucion
	//por eso se la pasas como parametro al run
    public static void main(String[] args) {
        SpringApplication.run(RestApiSyncGateway.class, args);
    }

    //El filter permite filtrar las peticiones, en nuestro caso tomando en cuenta las cabeceras.
    //Esto nos ayudara a resolver el problema del CORS (Cross origin) o peticiones que viene de otro origen.
    //Como nuestras peticiones al web service vienen desde varios dispositivos, debemos evitar el problema del CORS.
    //Por eso esto es importante.
    //Tambien en este metodo definimos que metodos HTTP vamos a permitir.
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

    //Todas esta lineas permiten mapear las variables que estan definidas
    //En el archivo application.properties estan las configuraciones.
    //Esto nos permite generar y deployar rapida y facilmente el servicio sin tener que recompilar
   
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
    
    @Value("${gateway.port}")
    private Integer gatewayPort;
    
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
    
    //Un Bean en Java es un objeto que encapsula muchos otros objetos en uno solo.
    //Los beans tienen un constructor con 0 argumentos y son serializables (se convierten en informacion que se puede pasar y reconstruir en otra parte/aplicacion).
    //Estos beans son los objetos que van a vivir durante la ejecucion esta clase.
    //Los vamos a usar y a reutilizar mucho (para conectar con la base de datos).
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

    //En cada metodo haremos el request mapping para mapear otro nivel de direccionamiento dentro de la clase.
    //Es decir es un nivel de mapeo interno al mapeo de esta clase (api). 
    //Asi, este RequestMapping /recursos esta en realidad mapeando a /api/recursos.
    //En el mapeo, lo que esta entre parentesis es contenido que queremos extraer a una variable,
    //en este caso llamada recursoId. Es decir que lo que venga despues de recursos/ sera mapeado a esa variable.
    //En Request Mapping el segundo parametroe permite definir a que metodo HTTP vamos a mapear esta funcion, como es GET.
    //En la firma del metodo, el parametro viene anotado con @PathVariable. Esto permite conectar la variable que esta en el mapping,
    //con la variable que representa al parametro en java y que utilizaremos en nuestro metodo.
    @RequestMapping(value="/recursos/{recursoId}", method= RequestMethod.GET)
    public Object getRecursoById(@PathVariable("recursoId") String todoId) {
        return DatabaseForRest.getById(bucket(), todoId);
    }

    //Recuerda que estos metodos get estan llamando a otra clase para poder realizar el query (consulta) directo contra el CouchBase Server
    //Y no contra el Sync Gateway. Porque no contra el Sync Gateway, pues porque estas consultas son solo lecturas y se puede hacer contra
    //el servidor Couchbase Server que es mas robusto y que tiene la capacidad de hacer consultas de tipo N1QL que es un sabor de SQL para Couchbase.
    //N1QL provee muchas de las ventajas de SQL a una base de Datos NoSQL como es Couchbase. Es lo mejor de ambos mundos.
    //OJO, todas las otras operaciones que modifiquen data (Post {Crear}, Put {Modificar}, Delete {Borrar}) deben hacerse contra el Sync Gateway.
    //Si no haces las operaciones de modificacion contra el Sync Gateway API, se pierden las cabeceras de sync en los objetos y no se logra la replicacion 
    //que hace el sync gateway contra todos los dispositivos. 
    //Sin la capacidad de replicar el sync gateway no tiene sentido, por eso es super importante tener esto en mente.
    @RequestMapping(value="/recursos", method= RequestMethod.GET)
    public Object getAllRecursos() {
    	//System.out.println("Llamando al metodo de traer todos los recursos");
        List<Map<String, Object>> recursosEncontrados = DatabaseForRest.getAll(bucket());
        for(Map<String,Object> m:recursosEncontrados)
        	System.out.println(m.values());
        return recursosEncontrados;
    }

    //Como la data para Delete viene en formato JSON, debo usar JSONObject para manipular la informacion de manera conveniente.
    //FIXME: Arreglar webservice porque no deberia recibir nada y debe estar autenticado
    /*@RequestMapping(value="/recursos", method= RequestMethod.DELETE)
    public Object deleteAllRecursos(@RequestBody String json) {
        JsonArray jsonData = JsonArray.fromJson(json);
        JsonArray responses = JsonArray.create();
        for(int i = 0; i < jsonData.size(); i++) {
            responses.add(makeDeleteRequest("http://" + gatewayHostname + ":4984/" + gatewayDatabase + "/" + jsonData.getObject(i).getString("id") + "?rev=" + jsonData.getObject(i).getString("rev")));
        }
        return new ResponseEntity<String>(responses.toString(), HttpStatus.OK);
    }*/
    
  //Como la data para Delete viene en formato JSON, debo usar JSONObject para manipular la informacion de manera conveniente.
    //FIXME: Arreglar webservice porque no deberia recibir nada y debe estar autenticado
    @RequestMapping(value="/recursos", method= RequestMethod.DELETE)
    public Object deleteAllRecursos() {
    		JsonObject allDocsRequestResponse = makeGetAllDocsRequest();
    		int requestResponseStatus = allDocsRequestResponse.getInt("status");
    		if(requestResponseStatus!=200)
    		{
    			 return new ResponseEntity<String>("Error en get all documents el bd.", HttpStatus.INTERNAL_SERVER_ERROR);
    		}
    		
    		JsonArray jsonData = allDocsRequestResponse.getObject("content").getArray("rows");
    		JsonArray docsToUpdate = JsonArray.create();
        //JsonArray responses = JsonArray.create();
        for(int i = 0; i < jsonData.size(); i++) {
        		JsonObject docOriginal = jsonData.getObject(i);
        		JsonObject doc = JsonObject.create();
        		doc.put("_id", docOriginal.getString("id"));
        		doc.put("_rev", docOriginal.getObject("value").getString("rev"));
        		doc.put("_deleted", true);
        		docsToUpdate.add(doc);
        }
        String url = "http://" + gatewayHostname + ":" + gatewayPort +"/" + gatewayDatabase + "/_bulk_docs";
        JsonObject updateBody = JsonObject.create();
        updateBody.put("docs", docsToUpdate);
        JsonObject bulkUpdateResponse = makeBulkPostRequest(url, updateBody.toString());
        int bulkUpdateResponseStatus = bulkUpdateResponse.getInt("status");
		if(bulkUpdateResponseStatus==409)
		{
			 return new ResponseEntity<String>("Error conflicto al borrar/actualizar documentos.", HttpStatus.CONFLICT);
		}
        
        return new ResponseEntity<String>(bulkUpdateResponse.toString(), HttpStatus.OK);
    }

    @RequestMapping(value="/recursos/{recursoId}", method= RequestMethod.DELETE)
    public Object deleteRecurso(@RequestParam(value="rev") String rev, @PathVariable("recursoId") String todoId) {
    	
    	String deleteRequest = "http://" + gatewayHostname + ":4984/" + gatewayDatabase + "/" + todoId.replaceAll(" ", "%20") + "?rev="+rev;
    	System.out.println("REV: " + rev);
        System.out.println("DeleteRequest: " + deleteRequest);
        JsonObject response = makeDeleteRequest(deleteRequest);
        System.out.println("Response: " + response);
        return new ResponseEntity<String>(response.getObject("content").toString(), HttpStatus.valueOf(response.getInt("status")));
    	
    }
    
    //La etiqueta @RequestBody ayuda a mapear la informacion que viene dentro del Request, de manera que este disponible pal metodo.
    @RequestMapping(value="/recursos", method= RequestMethod.POST)
    public Object createRecurso(@RequestBody String json) {
        JsonObject jsonData = JsonObject.fromJson(json);
        if(!jsonData.containsKey("descripcion")) {
            return new ResponseEntity<String>(JsonObject.create().put("error", 400).put("message", "El recurso debe tener una descripcion").toString(), HttpStatus.BAD_REQUEST);
        } else if(!jsonData.containsKey("posicion")) {
            return new ResponseEntity<String>(JsonObject.create().put("error", 400).put("message", "El recurso debe tener datos de geolocalizacion").toString(), HttpStatus.BAD_REQUEST);
        }
        
        //Borrar sync en este punto puesto que para post (nuevos objetos), no se necesita el campo sync.
        //Si se envia el campo sync, el webservice falla con error 
        //{"error":"Bad Request","reason":"user defined top level properties beginning with '_' are not allowed in document body"}
        if(jsonData.containsKey("_sync"))
        {
        		jsonData.removeKey("_sync");
        }
        
        JsonObject data = jsonData;

        data.put("_id", jsonData.get("nombre")).put("documentClass", "class es.codigoandroid.pojos.Recursos");
        System.out.println("Datos a guardar: " + data);
        
        JsonObject response = makePostRequest("http://" + gatewayHostname + ":4984/" + gatewayDatabase + "/", data.toString());
        return new ResponseEntity<String>(response.getObject("content").toString(), HttpStatus.valueOf(response.getInt("status")));
    }
    
    @RequestMapping(value="/recursos", method= RequestMethod.PUT)
    public Object updateRecurso(@RequestBody String json) {
    		System.out.println("Entro a put request de recurso");
        JsonObject jsonData = JsonObject.fromJson(json);
        String rev;
        
        //No olvidar hacer validaciones de acuerdo a lo que deseas que tenga tu data (json), osea los campos minimos que esperas se encuentren en la data.
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
        JsonObject data = jsonData;
        data.put("documentClass", "class es.codigoandroid.pojos.Recursos");
        //data.put("_id", jsonData.get("nombre")).put("documentClass", "class es.codigoandroid.pojos.Recursos");
        String putRequest = "http://" + gatewayHostname + ":" + gatewayPort +"/" + gatewayDatabase + "/" + jsonData.getString("id").replaceAll(" ", "%20") + "?rev="+rev;
        System.out.println("PutRequest: " + putRequest);
        System.out.println("Data: " + data.toString());
        JsonObject response = makePutRequest(putRequest, data.toString());
        return new ResponseEntity<String>(response.getObject("content").toString(), HttpStatus.valueOf(response.getInt("status")));
    }
    
    private JsonObject makeGetAllDocsRequest() {
        JsonObject jsonResult = JsonObject.create();
        try {
            HttpClient client = HttpClientBuilder.create().build();
            
            //Para Darle parametros al Get
            URIBuilder builder = new URIBuilder();
            builder.setScheme("http").setHost(gatewayHostname).setPort(4985).setPath("/"+gatewayDatabase+"/_all_docs")
                .setParameter("access", "false")
                .setParameter("channels", "false")
                .setParameter("revs", "false")
                .setParameter("include_docs", "false")
                .setParameter("update_seq", "false");
            URI uri = builder.build();
            HttpGet get = new HttpGet(uri);
            HttpResponse response = client.execute(get);
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
    
    private JsonObject makeBulkPostRequest(String url, String body) {
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

    //WS senderos
    @RequestMapping(value="/senderos", method= RequestMethod.GET)
    public Object getAllSenderos() {
    	System.out.println("Llamando al metodo de traer todos los senderos");
        List<Map<String, Object>> recursosEncontrados = DatabaseForRest.getAllSenderos(bucket());
        for(Map<String,Object> m:recursosEncontrados)
        	System.out.println(m.values());
        return recursosEncontrados;
    }
    @RequestMapping(value="/senderos/{senderoId}", method= RequestMethod.GET)
    public Object getSenderoById(@PathVariable("senderoId") String todoId) {
        return DatabaseForRest.getSenderoById(bucket(), todoId);
    }
    
    @RequestMapping(value="/senderos/{senderoId}/{nombreId}", method= RequestMethod.GET)
    public Object getNombreSenderoById(@PathVariable("senderoId") String todoId,@PathVariable("nombreId") String nombreId) {
    	//System.out.println(DatabaseForRest.getNombreSenderoById(bucket(),todoId,nombreId));

        return DatabaseForRest.getNombreSenderoById(bucket(),todoId,nombreId);
    }
    
    
    
    
    @RequestMapping(value="/sendero", method = RequestMethod.POST)
    public Object createSendero(@RequestBody String json) throws JsonProcessingException  {
    	String revSendero = "";
    	String idRecurso = "";
    	System.out.println("Entra al metodo");
    	JsonObject jsonData = JsonObject.fromJson(json);
    	System.out.println("Validar los datos");

        if(!jsonData.containsKey("descripcion")) {
            return new ResponseEntity<String>(JsonObject.create().put("error", 400).put("message", "El sendero debe tener una descripcion").toString(), HttpStatus.BAD_REQUEST);
        } else if(!jsonData.containsKey("nombre")) {
            return new ResponseEntity<String>(JsonObject.create().put("error", 400).put("message", "El sendero debe tener un nombre").toString(), HttpStatus.BAD_REQUEST);
        }
        //validar recursoId
        
        
        //senderos ingresado desde la aplicacion
        System.out.println("Sendero Ingresado: " + jsonData);
        jsonData.put("_id", jsonData.get("nombre")).put("documentClass", "class es.codigoandroid.pojos.Senderos");
        //recupero el recurso del sendero mediante el id del sendero ingresado
        String datosDeRecurso = new ObjectMapper().writeValueAsString(getRecursoById(jsonData.getString("RecursoId")));
        JsonObject jsonDatosRecurso = JsonObject.fromJson(datosDeRecurso);
        System.out.println("Datos de Recurso: " + jsonDatosRecurso);
        
        //capturar la rev y el id del recurso
        JsonObject jsonSync = JsonObject.fromJson(jsonDatosRecurso.get("_sync").toString());
        System.out.println("_sync : " + jsonSync);
        revSendero = jsonSync.getString("rev");
        idRecurso = jsonData.getString("RecursoId");
        System.out.println("rev: " + revSendero + " idRecurso: " + idRecurso);
        
        //convertir el json de senderos en Array
        JsonArray senderosArray = (JsonArray)jsonDatosRecurso.get("sendero");
        System.out.println("Array de senderos: " + senderosArray);
        
        //agrego el sendero ingresado 
        senderosArray.add(jsonData);
        
        System.out.println("Datos + otro sendero: " + senderosArray);
        
        //Agregar datos de los senderos 
        jsonData.removeKey("RecursoId");
        if(jsonDatosRecurso.containsKey("_sync"))
        {
        	jsonDatosRecurso.removeKey("_sync");
        }
        
        jsonDatosRecurso.put("sendero", senderosArray);
        System.out.println("Recurso Listo con los senderos: " + jsonDatosRecurso);
        
        //Elimino el Sendero Anterior
        deleteRecurso(revSendero,idRecurso);
        return createRecurso(jsonDatosRecurso.toString());
    }

    @RequestMapping(value="/senderos", method= RequestMethod.PUT)
    public Object updateSendero(@RequestBody String json) {
    	System.out.println("Entro a put request de sendero");
        JsonObject jsonData = JsonObject.fromJson(json);
        String rev;
        if(!jsonData.containsKey("nombre")) {
            return new ResponseEntity<String>(JsonObject.create().put("error", 400).put("message", "El sendero debe tener un nombre para hacer put").toString(), HttpStatus.BAD_REQUEST);
        }else{
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
        JsonObject data = jsonData;
        String putRequest = "http://" + gatewayHostname + ":4984/" + gatewayDatabase + "/" + jsonData.get("_id") + "?rev="+rev;
        System.out.println("PutRequest: " + putRequest);
        System.out.println("Data: " + data.toString());
        JsonObject response = makePutRequest(putRequest, data.toString());
        return new ResponseEntity<String>(response.getObject("content").toString(), HttpStatus.valueOf(response.getInt("status")));
    }
}