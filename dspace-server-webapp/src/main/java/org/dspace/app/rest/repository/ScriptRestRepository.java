/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.converter.DSpaceRunnableParameterConverter;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.ParameterValueRest;
import org.dspace.app.rest.model.ProcessRest;
import org.dspace.app.rest.model.ScriptRest;
import org.dspace.app.rest.scripts.handler.impl.RestDSpaceRunnableHandler;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.scripts.DSpaceCommandLineParameter;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.dspace.scripts.service.ScriptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * This is the REST repository dealing with the Script logic
 */
@Component(ScriptRest.CATEGORY + "." + ScriptRest.PLURAL_NAME)
public class ScriptRestRepository extends DSpaceRestRepository<ScriptRest, String> {

    private static final Logger log = LogManager.getLogger();

    @Autowired
    private ScriptService scriptService;

    @Autowired
    private DSpaceRunnableParameterConverter dSpaceRunnableParameterConverter;

    @Autowired
    private ObjectMapper mapper;

    @Override
    // authorization is verified inside the method
    @PreAuthorize("hasAuthority('AUTHENTICATED')")
    public ScriptRest findOne(Context context, String name) {
        ScriptConfiguration scriptConfiguration = scriptService.getScriptConfiguration(name);
        if (scriptConfiguration != null) {
            if (scriptConfiguration.isAllowedToExecute(context, null)) {
                return converter.toRest(scriptConfiguration, utils.obtainProjection());
            } else {
                throw new AccessDeniedException("The current user was not authorized to access this script");
            }
        }
        return null;
    }

    @Override
    // authorization check is performed inside the script service
    @PreAuthorize("hasAuthority('AUTHENTICATED')")
    public Page<ScriptRest> findAll(Context context, Pageable pageable) {
        List<ScriptConfiguration> scriptConfigurations =
            scriptService.getScriptConfigurations(context);
        return converter.toRestPage(scriptConfigurations, pageable, utils.obtainProjection());
    }

    @Override
    public Class<ScriptRest> getDomainClass() {
        return ScriptRest.class;
    }

    /**
     * This method will take a String scriptname parameter and it'll try to resolve this to a script known by DSpace.
     * If a script is found, it'll start a process for this script with the given properties to this request
     * @param scriptName    The name of the script that will try to be resolved and started
     * @return A ProcessRest object representing the started process for this script
     * @throws SQLException If something goes wrong
     * @throws IOException  If something goes wrong
     */
    public ProcessRest startProcess(Context context, String scriptName, List<MultipartFile> files) throws SQLException,
        IOException, AuthorizeException, IllegalAccessException, InstantiationException {
        String properties = requestService.getCurrentRequest().getServletRequest().getParameter("properties");
        List<DSpaceCommandLineParameter> dSpaceCommandLineParameters =
            processPropertiesToDSpaceCommandLineParameters(properties);
        ScriptConfiguration scriptToExecute = scriptService.getScriptConfiguration(scriptName);

        if (scriptToExecute == null) {
            throw new ResourceNotFoundException("The script for name: " + scriptName + " wasn't found");
        }
        try {
            if (!scriptToExecute.isAllowedToExecute(context, dSpaceCommandLineParameters)) {
                throw new AuthorizeException("Current user is not eligible to execute script with name: " + scriptName
                        + " and the specified parameters " + StringUtils.join(dSpaceCommandLineParameters, ", "));
            }
        } catch (IllegalArgumentException e) {
            throw new DSpaceBadRequestException("Illegal argoument " + e.getMessage(), e);
        }
        RestDSpaceRunnableHandler restDSpaceRunnableHandler = new RestDSpaceRunnableHandler(
            context.getCurrentUser(), scriptToExecute.getName(), dSpaceCommandLineParameters,
            new HashSet<>(context.getSpecialGroups()));
        List<String> args = constructArgs(dSpaceCommandLineParameters);
        runDSpaceScript(files, context, scriptToExecute, restDSpaceRunnableHandler, args);
        return converter.toRest(restDSpaceRunnableHandler.getProcess(context), utils.obtainProjection());
    }

    private List<DSpaceCommandLineParameter> processPropertiesToDSpaceCommandLineParameters(String propertiesJson)
        throws IOException {
        List<ParameterValueRest> parameterValueRestList = new LinkedList<>();
        if (StringUtils.isNotBlank(propertiesJson)) {
            parameterValueRestList = Arrays.asList(mapper.readValue(propertiesJson, ParameterValueRest[].class));
        }

        List<DSpaceCommandLineParameter> dSpaceCommandLineParameters = new LinkedList<>();
        dSpaceCommandLineParameters.addAll(
            parameterValueRestList.stream().map(x -> dSpaceRunnableParameterConverter.toModel(x))
                                  .collect(Collectors.toList()));
        return dSpaceCommandLineParameters;
    }

    private List<String> constructArgs(List<DSpaceCommandLineParameter> dSpaceCommandLineParameters) {
        List<String> args = new ArrayList<>();
        for (DSpaceCommandLineParameter parameter : dSpaceCommandLineParameters) {
            args.add(parameter.getName());
            if (parameter.getValue() != null) {
                args.add(parameter.getValue());
            }
        }
        return args;
    }

    private void runDSpaceScript(List<MultipartFile> files, Context context, ScriptConfiguration scriptToExecute,
                                 RestDSpaceRunnableHandler restDSpaceRunnableHandler, List<String> args)
        throws IOException, SQLException, AuthorizeException, InstantiationException, IllegalAccessException {
        DSpaceRunnable dSpaceRunnable = scriptService.createDSpaceRunnableForScriptConfiguration(scriptToExecute);
        try {
            dSpaceRunnable.initialize(args.toArray(new String[0]), restDSpaceRunnableHandler, context.getCurrentUser());
            if (files != null && !files.isEmpty()) {
                checkFileNames(dSpaceRunnable, files);
                processFiles(context, restDSpaceRunnableHandler, files);
            }
            restDSpaceRunnableHandler.schedule(dSpaceRunnable);
        } catch (ParseException e) {
            dSpaceRunnable.printHelp();
            try {
                restDSpaceRunnableHandler.handleException(
                    "Failed to parse the arguments given to the script with name: "
                        + scriptToExecute.getName() + " and args: " + args, e
                );
            } catch (Exception re) {
                // ignore re-thrown exception
            }
        }
    }

    private void processFiles(Context context, RestDSpaceRunnableHandler restDSpaceRunnableHandler,
                              List<MultipartFile> files)
        throws IOException, SQLException, AuthorizeException {
        for (MultipartFile file : files) {
            restDSpaceRunnableHandler
                .writeFilestream(context, file.getOriginalFilename(), file.getInputStream(), "inputfile");
        }
    }

    /**
     * This method checks if the files referenced in the options are actually present for the request
     * If this isn't the case, we'll abort the script now instead of creating issues later on
     * @param dSpaceRunnable   The script that we'll attempt to run
     * @param files             The list of files in the request
     */
    private void checkFileNames(DSpaceRunnable dSpaceRunnable, List<MultipartFile> files) {
        List<String> fileNames = new LinkedList<>();
        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            if (fileNames.contains(fileName)) {
                throw new UnprocessableEntityException("There are two files with the same name: " + fileName);
            } else {
                fileNames.add(fileName);
            }
        }

        List<String> fileNamesFromOptions = dSpaceRunnable.getFileNamesFromInputStreamOptions();
        if (!fileNames.containsAll(fileNamesFromOptions)) {
            throw new UnprocessableEntityException("Files given in properties aren't all present in the request");
        }
    }


}
