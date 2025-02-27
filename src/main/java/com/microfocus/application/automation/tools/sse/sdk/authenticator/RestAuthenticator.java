/*
 * Certain versions of software and/or documents ("Material") accessible here may contain branding from
 * Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 * the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 * and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 * marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * (c) Copyright 2012-2023 Micro Focus or one of its affiliates.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ___________________________________________________________________
 */

package com.microfocus.application.automation.tools.sse.sdk.authenticator;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.microfocus.adm.performancecenter.plugins.common.rest.RESTConstants;
import com.microfocus.application.automation.tools.sse.sdk.Base64Encoder;
import com.microfocus.application.automation.tools.sse.sdk.Client;
import com.microfocus.application.automation.tools.sse.sdk.Logger;
import com.microfocus.application.automation.tools.sse.sdk.ResourceAccessLevel;
import com.microfocus.application.automation.tools.sse.sdk.Response;

/**
 * @author Effi Bar-She'an
 * @author Dani Schreiber
 */

public class RestAuthenticator implements Authenticator {

    public static final String IS_AUTHENTICATED = "rest/is-authenticated";
    public static final String AUTHENTICATE_HEADER = "WWW-Authenticate";
    public static final String AUTHENTICATION_INFO = "AuthenticationInfo";
    public static final String USER_NAME = "Username";
    public static final String AUTHENTICATE_POINT = "authentication-point/authenticate";

    private String authenticationPoint;
    private Logger logger;
    
    public boolean login(Client client, String username, String password, String clientType, Logger logger) {
        this.logger = logger;

        this.logger.log("Start login to ALM server...");
        if (isAuthenticated(client)) {
            return true;
        }
        prepareAuthenticationPoint(client);
        boolean ret = authenticate(client, authenticationPoint, username, password);
        if (ret) {
            ret = appendQCSessionCookies(client, clientType);
        }
        return ret;
    }

    /**
     * Some customer always got wrong authenticate point because of an issue of ALM.
     *          But they still can login with a right authenticate point.
     *          So try to login with that anyway.
     * @param client
     */
    private void prepareAuthenticationPoint(Client client) {
        if (authenticationPoint != null && !isAuthenticatePointRight(authenticationPoint, client.getServerUrl())) {
            authenticationPoint = null;
        }
        if (authenticationPoint == null) {
            authenticationPoint = client.getServerUrl().endsWith("/") ?
                    client.getServerUrl() + AUTHENTICATE_POINT :
                    client.getServerUrl() + "/" + AUTHENTICATE_POINT;
        }
        logger.log("Try to authenticate through: " + authenticationPoint);
    }

    /**
     * Some ALM server generates wrong authenticate point and port.
     * @param authenticatePointStr
     * @param serverUrlStr
     * @return
     */
    private boolean isAuthenticatePointRight(String authenticatePointStr, String serverUrlStr) {
        URL serverUrl;
        URL authenticatePoint;

        try {
            serverUrl = new URL(serverUrlStr);
        } catch (MalformedURLException e) {
            logger.log(String.format("Server url %s is not a valid url.", e.getMessage()));
            return false;
        }

        try {
            authenticatePoint = new URL(authenticatePointStr);
        } catch (MalformedURLException e) {
            logger.log(String.format("Authenticate Point url %s is not a valid url.", e.getMessage()));
            return false;
        }

        boolean result = serverUrl.getProtocol().equalsIgnoreCase(authenticatePoint.getProtocol())
                && serverUrl.getPort() == authenticatePoint.getPort();

        if (!result) {
            logger.log("Authenticate point schema or port is different with server's. Please check with ALM site admin.");
        }
        return result;
    }
    
    /**
     * @param loginUrl
     *            to authenticate at
     * @return true on operation success, false otherwise Basic authentication (must store returned
     *         cookies for further use)
     */
    private boolean authenticate(Client client, String loginUrl, String username, String password) {
        // create a string that looks like:
        // "Basic ((username:password)<as bytes>)<64encoded>"
        byte[] credBytes = (username + ":" + password).getBytes();
        String credEncodedString = "Basic " + Base64Encoder.encode(credBytes);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put(RESTConstants.AUTHORIZATION, credEncodedString);
        Response response = client.httpGet(loginUrl, null, headers, ResourceAccessLevel.PUBLIC);

        boolean ret = response.isOk();
        if (ret) {
            logger.log(String.format(
                    "Logged in successfully to ALM Server %s using %s",
                    client.getServerUrl(),
                    username));
        } else {
            logger.log(String.format(
                    "Login to ALM Server at %s failed. Status Code: %s",
                    client.getServerUrl(),
                    response.getStatusCode()));
        }
        return ret;
    }

    /**
     * @return true if logout successful
     * @throws Exception
     *             close session on server and clean session cookies on client
     */
    public boolean logout(Client client, String username) {
        // note the get operation logs us out by setting authentication cookies to:
        // LWSSO_COOKIE_KEY="" via server response header Set-Cookie
        Response response =
                client.httpGet(
                        client.build("authentication-point/logout"),
                        null,
                        null,
                        ResourceAccessLevel.PUBLIC);
        return response.isOk();
    }

    /**
     * Verify is the client is already authenticated. If not, try get the authenticate point.
     * @param client
     * @return
     */
    private boolean isAuthenticated(Client client) {
        Response response =
                client.httpGet(
                        client.build(IS_AUTHENTICATED),
                        null,
                        null,
                        ResourceAccessLevel.PUBLIC);
        
        if (checkAuthResponse(response, client.getUsername())) {
            return true;
        }

        // Try to get authenticate point regardless the response status.
        authenticationPoint = getAuthenticatePoint(response);

        if (authenticationPoint == null) {
            logger.log(String.format("Failed to get authenticate authenticate point. Exception %s", response.getFailure()));
        }
        else {
            authenticationPoint = authenticationPoint.replace("\"", "");
            authenticationPoint += "/authenticate";
            logger.log("Got authenticate point:" + authenticationPoint);
        }

        return false;
    }
    
    private boolean checkAuthResponse(Response response, String authUser) {
        if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {
            if (response.getData() != null
                    && new String(response.getData()).contains(AUTHENTICATION_INFO)
                    && new String(response.getData()).contains(USER_NAME)
                    && new String(response.getData()).contains(authUser)) {
                logger.log(String.format("Already logged in to ALM Server using %s", authUser));
                return true;
            }
            logger.log("Failed to check authenticate response header.");
            return false;
        }

        if (response.getStatusCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
            logger.log(String.format("User %s unauthorized.", authUser));
            return false;
        }

        logger.log(String.format("Failed to check authenticate status. Exception: %s", response.getFailure()));
        return false;
    }

    /**
     * Try get authenticate point from response.
     * @param response response
     * @return null or authenticate point
     */
    private String getAuthenticatePoint(Response response) {
        Map<String, List<String>> headers = response.getHeaders();
        if (headers == null || headers.size() == 0) {
            return null;
        }
        if (headers.get(AUTHENTICATE_HEADER) == null || headers.get(AUTHENTICATE_HEADER).isEmpty()) {
            return null;
        }
        String authenticateHeader = headers.get(AUTHENTICATE_HEADER).get(0);
        String[] authenticateHeaderArray = authenticateHeader.split("=");
        if (authenticateHeaderArray.length == 1) {
            return null;
        }
        return authenticateHeaderArray[1];
    }

    private boolean appendQCSessionCookies(Client client, String clientType) {
        logger.log("Creating session...");
        Map<String, String> headers = new HashMap<>();
        headers.put(RESTConstants.CONTENT_TYPE, RESTConstants.APP_XML);
        headers.put(RESTConstants.ACCEPT, RESTConstants.APP_XML);

        // issue a post request so that cookies relevant to the QC Session will be added to the RestClient
        Response response =
                client.httpPost(
                        client.build("rest/site-session"),
                        generateClientTypeData(clientType),
                        headers,
                        ResourceAccessLevel.PUBLIC);
        boolean ret = response.isOk();
        if (!ret) {
            logger.log(String.format("Cannot append QCSession cookies. Exception: %s", response.getFailure()));
        } else {
            logger.log("Session created.");
        }
        return ret;
    }

    private byte[] generateClientTypeData(String clientType) {
        String data = String.format("<session-parameters><client-type>%s</client-type></session-parameters>", clientType);
        return data.getBytes();
    }
}
