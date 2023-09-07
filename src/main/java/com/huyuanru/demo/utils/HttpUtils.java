package com.huyuanru.demo.utils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

/**
 * HTTPS 调用接口
 *
 */

@Slf4j
public class HttpUtils {

    //获取接口数据
    public static String post(String url ,String requestBody){
        String result = null;
        DefaultHttpClient httpclient = (DefaultHttpClient) wrapClient(new DefaultHttpClient());
        try {
            HttpPost httpPost = new HttpPost(url);
            httpPost.addHeader("Content-type","application/json");
            httpPost.setHeader("Accept", "*/*");
            httpPost.setEntity(new StringEntity(requestBody, Charset.forName("UTF-8").toString()));
            try {
                HttpResponse httpResponse = httpclient.execute(httpPost);
                HttpEntity entity = httpResponse.getEntity();
                System.out.println(httpResponse.getStatusLine());
                if(entity!=null){
                    result = EntityUtils.toString(entity,Charset.forName("UTF-8").toString());
                }
            } catch (ClientProtocolException e) {
                log.error("获取第三方接口数据ClientProtocolException异常错误:" + e.getMessage());
            } catch (IOException e) {
                log.error("获取第三方接口数据IOException异常错误:" + e.getMessage());
            }
        } catch (Exception e) {
            log.error("获取第三方接口数据UnsupportedEncodingException异常错误:" + e.getMessage());
        }
        return result;
    }

    //获取接口数据
    public static String get(String url){
        String result = null;
        DefaultHttpClient httpclient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("Content-type","application/json; charset=utf-8");
        httpGet.setHeader("Accept", "application/json");
        try {
            HttpResponse httpResponse = httpclient.execute(httpGet);
            HttpEntity entity = httpResponse.getEntity();
            System.out.println(httpResponse.getStatusLine());
            if(entity!=null){
                result = EntityUtils.toString(entity,Charset.forName("UTF-8").toString());
            }
        } catch (ClientProtocolException e) {
            log.error("获取第三方接口数据ClientProtocolException异常错误:" + e.getMessage());
        } catch (IOException e) {
            log.error("获取第三方接口数据IOException异常错误:" + e.getMessage());
        }
        return result;
    }

    //参数base就是我们创建的DefaultHttpClient对象
    public static HttpClient wrapClient(HttpClient base)
    {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            X509TrustManager tm = new X509TrustManager() {

                public X509Certificate[] getAcceptedIssuers()
                {
                    // TODO Auto-generated method stub
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] arg0,
                                               String arg1) throws CertificateException {
                    // TODO Auto-generated method stub

                }

                public void checkServerTrusted(X509Certificate[] arg0,
                                               String arg1) throws CertificateException {
                    // TODO Auto-generated method stub

                }

            };
            ctx.init(null, new TrustManager[] { tm }, null);
            SSLSocketFactory ssf = new SSLSocketFactory(ctx);
            ssf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            ClientConnectionManager ccm = base.getConnectionManager();
            SchemeRegistry sr = ccm.getSchemeRegistry();
            //设置要使用的端口，默认是443
            sr.register(new Scheme("https", ssf, 443));
            return new DefaultHttpClient(ccm, base.getParams());
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
