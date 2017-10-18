/*
 *
 *  The contents of this file are subject to the Terracotta Public License Version
 *  2.0 (the "License"); You may not use this file except in compliance with the
 *  License. You may obtain a copy of the License at
 *
 *  http://terracotta.org/legal/terracotta-public-license.
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 *  the specific language governing rights and limitations under the License.
 *
 *  The Covered Software is Terracotta Core.
 *
 *  The Initial Developer of the Covered Software is
 *  Terracotta, Inc., a Software AG company
 *
 */
package com.tc.util.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tc.exception.TCRuntimeException;
import com.tc.net.core.SecurityInfo;
import com.tc.security.PwProvider;
import com.tc.security.TCAuthenticationException;
import com.tc.security.TCAuthorizationException;
import com.tc.util.Assert;
import com.tc.util.concurrent.ThreadUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@SuppressWarnings("restriction")
public class ServerURL {

  private static final Logger logger = LoggerFactory.getLogger(ServerURL.class);

  private static final boolean  DISABLE_HOSTNAME_VERIFIER = Boolean.getBoolean("tc.ssl.disableHostnameVerifier");
  private static final boolean  TRUST_ALL_CERTS           = Boolean.getBoolean("tc.ssl.trustAllCerts");

  private final URL             theURL;
  private final int             timeout;
  private final SecurityInfo    securityInfo;

  public ServerURL(String host, int port, String file, SecurityInfo securityInfo) throws MalformedURLException {
    this(host, port, file, -1, securityInfo);
  }

  public ServerURL(String host, int port, String file, int timeout, SecurityInfo securityInfo)
      throws MalformedURLException {
    this.timeout = timeout;
    this.securityInfo = securityInfo;
    this.theURL = new URL(securityInfo.isSecure() ? "https" : "http", host, port, file);
  }

  public InputStream openStream() throws IOException {
    return this.openStream(null);
  }

  public String getHeaderField(String fieldName, PwProvider pwProvider, boolean retryOnNull) throws IOException {
    for (int i = 0; i < 3; i++) {
      URLConnection urlConnection = createSecureConnection(pwProvider);
      urlConnection.connect();
      //Check response code to make sure everything works fine.
      int responseCode = ((HttpURLConnection) urlConnection).getResponseCode();
      switch (responseCode) {
        case 401:
          throw new TCAuthenticationException("Authentication error connecting to " + urlConnection.getURL()
              + " - invalid credentials (tried user " + securityInfo.getUsername()
              + ")");
        case 403:
          throw new TCAuthorizationException("Authorization error connecting to " + urlConnection.getURL()
              + " - does the user '" + securityInfo.getUsername()
              + "' have the required roles?");
        default:
      }

      String value = urlConnection.getHeaderField(fieldName);
      if (value != null || !retryOnNull) { return value; }

      logger.info("Retrying connection since header field was null");
      ThreadUtil.reallySleep(50);
    }

    throw new RuntimeException("Cannot retrieve " + fieldName + " header from server url: " + theURL);
  }

  public InputStream openStream(PwProvider pwProvider) throws IOException {
    URLConnection urlConnection = createSecureConnection(pwProvider);

    try {
      return urlConnection.getInputStream();
    } catch (IOException e) {
      if (urlConnection instanceof HttpURLConnection) {
        int responseCode = ((HttpURLConnection) urlConnection).getResponseCode();
        switch (responseCode) {
          case 401:
            throw new TCAuthenticationException("Authentication error connecting to " + urlConnection.getURL()
                                                + " - invalid credentials (tried user " + securityInfo.getUsername()
                                                + ")", e);
          case 403:
            throw new TCAuthorizationException("Authorization error connecting to " + urlConnection.getURL()
                                               + " - does the user '" + securityInfo.getUsername()
                                               + "' have the required roles?", e);
          default:
        }
      }
      throw e;
    }
  }

  private URLConnection createSecureConnection(PwProvider pwProvider) {
    if (securityInfo.isSecure()) {
      Assert.assertNotNull("Secured URL '" + theURL + "', yet PwProvider instance", pwProvider);
    }

    URLConnection urlConnection;
    try {
      urlConnection = theURL.openConnection();
      String uri = null;
    } catch (IOException e1) {
      throw new IllegalStateException(e1);
    }

    if (timeout > -1) {
      urlConnection.setConnectTimeout(timeout);
      urlConnection.setReadTimeout(timeout);
    }
    return urlConnection;
  }

  @Override
  public String toString() {
    return theURL.toString();
  }

  public String getUsername() {
    return securityInfo.isSecure() ? securityInfo.getUsername() : null;
  }

  private static void tweakSecureConnectionSettings(URLConnection urlConnection) {
    HttpsURLConnection sslUrlConnection;

    try {
      sslUrlConnection = (HttpsURLConnection) urlConnection;
    } catch (ClassCastException e) {
      throw new IllegalStateException(
                                      "Unable to cast "
                                          + urlConnection
                                          + " to javax.net.ssl.HttpsURLConnection. "
                                          + "Options tc.ssl.trustAllCerts and tc.ssl.disableHostnameVerifier are causing this issue.",
                                      e);
    }

    if (DISABLE_HOSTNAME_VERIFIER) {
      // don't verify hostname
      sslUrlConnection.setHostnameVerifier(new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
          return true;
        }
      });
    }

    TrustManager[] trustManagers = null;
    if (TRUST_ALL_CERTS) {
      // trust all certs
      trustManagers = new TrustManager[] { new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
          //
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
          //
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return null;
        }
      } };
    }

    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustManagers, null);
      sslUrlConnection.setSSLSocketFactory(sslContext.getSocketFactory());
    } catch (Exception e) {
      throw new RuntimeException("unable to create SSL connection from " + urlConnection.getURL(), e);
    }
  }
}