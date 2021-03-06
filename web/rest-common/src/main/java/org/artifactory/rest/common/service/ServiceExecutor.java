package org.artifactory.rest.common.service;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
/**
 * @author Chen Keinan
 */
@Component("serviceUiExecutor")
public class ServiceExecutor {
    private static final Logger log = LoggerFactory.getLogger(ServiceExecutor.class);

    public Response process(ArtifactoryRestRequest restReq, RestResponse restRes, RestService serviceAction) {
        log.trace("calling rest service :" + serviceAction.getClass().getSimpleName());
        // execute service method
        serviceAction.execute(restReq,restRes);
        // build response
        Response response = restRes.buildResponse();
        return response;
    }
}
