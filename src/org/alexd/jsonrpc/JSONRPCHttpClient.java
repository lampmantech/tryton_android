package org.alexd.jsonrpc;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.net.Socket;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

/**
 * Implementation of JSON-RPC over HTTP/POST
 */
public class JSONRPCHttpClient extends JSONRPCClient
{

	/*
	 * HttpClient to issue the HTTP/POST request
	 */
	private HttpClient httpClient;
	/*
	 * Service URI
	 */
	private String serviceUri;
	
	// HTTP 1.1
	private static final ProtocolVersion PROTOCOL_VERSION = new ProtocolVersion("HTTP", 1, 1);
	
 	/**
	 * Construct a JsonRPCClient with the given httpClient and service uri
	 *
	 * @param client
	 *            httpClient to use
	 * @param uri
	 *            uri of the service
	 */
	public JSONRPCHttpClient(HttpClient cleint, String uri){
		httpClient = cleint;
		serviceUri = uri;
	}
	
	private class AllowAllSSLSocketFactory extends SSLSocketFactory {
		SSLContext sslContext = SSLContext.getInstance("TLS");
		public AllowAllSSLSocketFactory( java.security.KeyStore truststore) throws Exception {
			super(truststore);
			X509TrustManager acceptAll = new X509TrustManager() {
				public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
				public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
				public boolean isClientTrusted(X509Certificate[] chain) { return true; }
				public boolean isServerTrusted(X509Certificate[] chain) { return true; }
				public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[] {}; }
			};
			sslContext.init(null, new TrustManager[] { acceptAll }, null);
		}
		public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, java.net.UnknownHostException {
			return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
		}
		public Socket createSocket() throws IOException {
			return sslContext.getSocketFactory().createSocket();
		}
	}
	
	/**
	 * Construct a JsonRPCClient with the given service uri
	 * 
	 * @param uri
	 *            uri of the service
	 */
	public JSONRPCHttpClient(String uri)
	{
		SSLSocketFactory allowAllsslFactory = null;
		try {
			allowAllsslFactory = new AllowAllSSLSocketFactory(null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		allowAllsslFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		// Quick ugly fix to accept ssl certificates
	    
		HttpParams params = new BasicHttpParams();
		SchemeRegistry registry = new SchemeRegistry();
		registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		registry.register(new Scheme("https", allowAllsslFactory, 443));
 
		ThreadSafeClientConnManager ccm = new ThreadSafeClientConnManager(params, registry);
		httpClient = new DefaultHttpClient(ccm, params);
		serviceUri = uri;
	}

	protected JSONObject doJSONRequest(JSONObject jsonRequest) throws JSONRPCException
	{
		// Create HTTP/POST request with a JSON entity containing the request
		HttpPost request = new HttpPost(serviceUri);
		HttpParams params = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(params, getConnectionTimeout());
		HttpConnectionParams.setSoTimeout(params, getSoTimeout());
		HttpProtocolParams.setVersion(params, PROTOCOL_VERSION);
		request.setParams(params);

		if(_debug){
			Log.i(JSONRPCHttpClient.class.toString(), "Request: " + jsonRequest.toString());
		}
		
		HttpEntity entity;
		
		try
		{
			if(encoding.length() > 0){
				entity = new JSONEntity(jsonRequest, encoding);
			}
			else{
				entity = new JSONEntity(jsonRequest);
			}
		}
		catch (UnsupportedEncodingException e1)
		{
			throw new JSONRPCException("Unsupported encoding", e1);
		}
                ((JSONEntity)entity).setContentEncoding("identity");
		request.setEntity(entity);
		
		try
		{
			// Execute the request and try to decode the JSON Response
			long t = System.currentTimeMillis();
			HttpResponse response = httpClient.execute(request);
			
			
			t = System.currentTimeMillis() - t;
                        String responseString = EntityUtils.toString(response.getEntity());


                        responseString = responseString.trim();
			
			if(_debug){
				Log.i(JSONRPCHttpClient.class.toString(), "Response: " + responseString);
			}
			
			JSONObject jsonResponse = new JSONObject(responseString);
			// Check for remote errors
			if (jsonResponse.has("error"))
			{
				Object jsonError = jsonResponse.get("error");
				if (!jsonError.equals(null))
					throw new JSONRPCException(jsonResponse.get("error"));
				return jsonResponse; // JSON-RPC 1.0
			}
			else
			{
				return jsonResponse; // JSON-RPC 2.0
                        }
		}
		// Underlying errors are wrapped into a JSONRPCException instance
		catch (ClientProtocolException e)
		{
			throw new JSONRPCException("HTTP error", e);
		}
                catch (IOException e)
		{
                    throw new JSONRPCException("IO error", e);
		}
		catch (JSONException e)
		{
			throw new JSONRPCException("Invalid JSON response", e);
                }
	}
}
