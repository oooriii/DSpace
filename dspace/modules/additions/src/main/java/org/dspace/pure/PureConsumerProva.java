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

/**
 * @author Oriol Olivé (oriol dot olive at udg dot edu)
 */
public class PureConsumerProva implements Consumer {
    /**
     * log4j logger
     */
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(PureConsumerProva.class);

    ConfigurationService configurationService;

    @Override
    public void initialize() throws Exception {
        // nothing to do
        // we can ask spring to give as a properly setuped instance of
        // DOIIdentifierProvider. Doing so we don't have to configure it and
        // can load it in consume method as this is not very expensive.
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    }

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
                .header("Authorization", dispatcherApiKey) // Changed to match the authorization method in dispatcher.py
                .header("Content-Type", "application/json")
                .build();

            // http request
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Dispatcher API response: " + response.body());
        } catch (Exception e) {
            log.error("Exception occurred while making request to dispatcher API: " + e.getMessage());
        }
        
        
        

    }

    @Override
    public void end(Context ctx) throws Exception {


    }

    @Override
    public void finish(Context ctx) throws Exception {
        // nothing to do
    }


}
