/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pure;

import java.sql.SQLException;

import org.apache.logging.log4j.Logger;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.logic.Filter;
import org.dspace.content.logic.FilterUtils;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.identifier.DOI;
import org.dspace.identifier.DOIIdentifierProvider;
import org.dspace.identifier.IdentifierException;
import org.dspace.identifier.IdentifierNotApplicableException;
import org.dspace.identifier.factory.IdentifierServiceFactory;
import org.dspace.identifier.service.DOIService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.dspace.workflow.factory.WorkflowServiceFactory;

import org.dspace.content.DSpaceObject;
import java.util.UUID;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;

/*
import java.lang.Process;
import java.lang.Runtime;
import java.net.http.HttpHeaders;
import java.util.Optional;
*/

/**
 * @author Oriol Olivé (oriol dot olive at udg dot edu)
 */
public class PureConsumerProva implements Consumer {
    /**
     * log4j logger
     */
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(PureConsumerProva.class);

    private final List<UUID> itemIDsToSync = new ArrayList<>();

    ConfigurationService configurationService;

    @Override
    public void initialize() throws Exception {
        // nothing to do
        // we can ask spring to give as a properly setuped instance of
        // DOIIdentifierProvider. Doing so we don't have to configure it and
        // can load it in consume method as this is not very expensive.
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    }

/*   posa a end de dispatcher
    // as we use asynchronous metadata update, our updates are not very expensive.
    // so we can do everything in the consume method.
    @Override
    public void consume(Context ctx, Event event) throws Exception {
 
        
        // get the PURE API URL
        String pureApiUrl = configurationService.getProperty("pure.api.url");
        if (pureApiUrl == null) {
            log.warn("PURESyncConsumer cannot get PURE API URL, skipping: " + event.toString());
            return;
        }
        // get the PURE API key
        String pureApiKey = configurationService.getProperty("pure.api.key");
        if (pureApiKey == null) {
            log.warn("PURESyncConsumer cannot get PURE API key, skipping: " + event.toString());
            return;
        }

        // get metadata name from pure id
        String metadataName = configurationService.getProperty("pure.metadata.name");
        if (metadataName == null) {
            // default to "pure_id"
            metadataName = "dc.identifier.gerioid";
        }

        // get the id of the object
        //DSpaceObject object = event.getObject(ctx);
        //String objectId = object.getID().toString();

        // prova
        //DSpaceObject object = event.getSubject(ctx);

        // get the id of the object
        String objectId = event.getSubjectID().toString();

        // log the metadata name
        log.info("--------------------------------");
        log.info("Event PURE CONSUMER PROVA: ");
        log.info("Metadata Name: " + metadataName);
        log.info("Pure API URL: " + pureApiUrl);
        //log.info("Pure API Key: " + pureApiKey);
        log.info("Object ID: " + objectId);
        log.info("--------------------------------");
        
        // make request to dispatcher api
        String dispatcherApiUrl = configurationService.getProperty("dispatcher.api.url");
        if (dispatcherApiUrl == null) {
            log.warn("PURESyncConsumer cannot get dispatcher API URL, skipping: " + event.toString());
            return;
        }
        String dispatcherApiKey = configurationService.getProperty("dispatcher.api.key");
        if (dispatcherApiKey == null) {
            log.warn("PURESyncConsumer cannot get dispatcher API key, skipping: " + event.toString());
            return;
        }
        try {
            // make request to dispatcher api
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(dispatcherApiUrl + "/dispatch/" + objectId))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Authorization", dispatcherApiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "DSpace-Pure-Integration")
                .GET()
                .build();

            try {
                // http request
                CompletableFuture<HttpResponse<String>> response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                HttpResponse<String> httpResponse = response.join();
                
                log.info("Dispatcher API response status: " + httpResponse.statusCode());
                log.info("Dispatcher API response headers: " + httpResponse.headers());
                log.info("Dispatcher API response body: " + httpResponse.body());
                
                if (httpResponse.statusCode() != 200) {
                    log.error("Dispatcher API returned error status: " + httpResponse.statusCode());
                }
            } catch (Exception e) {
                log.error("Exception occurred while making request to dispatcher API: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("Exception occurred while making request to dispatcher API: " + e.getMessage());
        }
        
            

    }

*/

    @Override
    public void consume(Context ctx, Event event) throws Exception {
        // Desa l'UUID per després
        UUID objectId = event.getSubjectID();
        log.info("Queued item for sync after commit: {}", objectId);
        itemIDsToSync.add(objectId);
    }

    @Override
    public void end(Context ctx) {
        if (itemIDsToSync.isEmpty()) {
            log.info("No items to sync with dispatcher API");
            return;
        }

        log.info("Starting dispatcher API sync for {} items - END", itemIDsToSync.size());
        
        // Debug configuration
        String dispatcherApiUrl = configurationService.getProperty("dispatcher.api.url");
        String dispatcherApiKey = configurationService.getProperty("dispatcher.api.key");
        log.info("Configuration loaded - URL: {}, Key present: {}", 
                dispatcherApiUrl, 
                dispatcherApiKey != null ? "Yes" : "No");
        
        // Add a delay before making the request to ensure item is created
        try {
            Thread.sleep(5000); // 5 second delay
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for item creation", e);
        }
        
        for (UUID itemId : itemIDsToSync) {
            try {
                // Verify item exists in DSpace before calling dispatcher
                if (verifyItemExists(ctx, itemId)) {
                    callDispatcherApi(itemId);
                } else {
                    log.error("Item {} not found in DSpace, skipping dispatcher call", itemId);
                }
            } catch (Exception e) {
                log.error("Failed to sync item {} with dispatcher API: {}", itemId, e.getMessage(), e);
            }
        }
        
        itemIDsToSync.clear();
        log.info("Dispatcher API sync completed - END");
    }

    private boolean verifyItemExists(Context ctx, UUID itemId) {
        try {
            Item item = ContentServiceFactory.getInstance().getItemService().find(ctx, itemId);
            if (item != null) {
                log.info("Verified item {} exists in DSpace", itemId);
                return true;
            }
            log.warn("Item {} not found in DSpace", itemId);
            return false;
        } catch (SQLException e) {
            log.error("Error verifying item {}: {}", itemId, e.getMessage());
            return false;
        }
    }

    private void callDispatcherApi(UUID itemId) {
        String dispatcherApiUrl = configurationService.getProperty("dispatcher.api.url");
        String dispatcherApiKey = configurationService.getProperty("dispatcher.api.key");

        if (dispatcherApiUrl == null || dispatcherApiKey == null) {
            log.error("Dispatcher API configuration missing. URL: {}, KEY: {}", 
                     dispatcherApiUrl, 
                     dispatcherApiKey != null ? "[REDACTED]" : "null");
            return;
        }

        String fullUrl = dispatcherApiUrl + "/dispatch/" + itemId;
        log.info("Preparing to call dispatcher API - URL: {}", fullUrl);

        try {
            // Create a new HttpClient with specific configuration
            HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();

            // Build the request with explicit headers
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("Authorization", dispatcherApiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "DSpace-Pure-Integration")
                    .GET()
                    .build();

            log.info("Sending request to dispatcher API for item {}", itemId);
            
            // Make the request synchronously
            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Log the complete response details
            log.info("Response received - Status: {}, Headers: {}", 
                    httpResponse.statusCode(),
                    httpResponse.headers());
            log.info("Response body: {}", httpResponse.body());

            if (httpResponse.statusCode() != 200) {
                log.error("Dispatcher API returned error - Status: {}, Body: {}", 
                        httpResponse.statusCode(),
                        httpResponse.body());
            } else {
                log.info("Successfully processed item {}", itemId);
            }
        } catch (Exception e) {
            log.error("Error calling dispatcher API for item {}: {}", itemId, e.getMessage(), e);
            // Log the full stack trace
            log.error("Stack trace:", e);
            throw new RuntimeException("Failed to call dispatcher API", e);
        }
    }

}
