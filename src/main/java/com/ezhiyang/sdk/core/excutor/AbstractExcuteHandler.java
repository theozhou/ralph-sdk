package com.ezhiyang.sdk.core.excutor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ezhiyang.sdk.core.context.SdkContext;
import com.ezhiyang.sdk.core.exception.SdkException;
import com.ezhiyang.sdk.core.http.HttpClient;
import com.ezhiyang.sdk.core.model.BaseReturnVo;
import com.ezhiyang.sdk.core.model.RequestWrapper;
import com.ezhiyang.sdk.core.model.RequestWrapper.HttpBodyType;
import com.ezhiyang.sdk.core.model.RequestWrapper.HttpMethodName;
import com.ezhiyang.sdk.core.model.ResponseWrapper;
import com.ezhiyang.sdk.util.JsonUtils;
import com.ezhiyang.sdk.util.SignUtils;

/**
 * AbstractExcuteHandler
 * @author Theo Zhou
 *
 * @param <R>
 * @param <D>
 */
public abstract class AbstractExcuteHandler<R extends BaseReturnVo,D> implements Serializable{

  private static final long serialVersionUID = -5201314L;

  private static Logger logger = LoggerFactory.getLogger(AbstractExcuteHandler.class);
  
  /**
   * get type code
   * @return
   */
  protected abstract String getTypeCode();
  
  /**
   * 执行接口
   * @param context
   * @return
   */
  public R excute(SdkContext context) {
    ResponseWrapper response = null;
    RequestWrapper request = new RequestWrapper();
    try {
      request.setBody(buildBody());
      request.setUrl(context.getOpenUrl());
      request.setMethodName(HttpMethodName.POST);
      request.setBodyType(HttpBodyType.JSON);
      
      boolean valid = context.authentication(request);
      
      if(!valid) {
        throw new SdkException("接口认证失败");
      }
      
      HttpClient client = HttpClient.getInstance();
      
      onBeforeSend(request,context);
      response = client.execute(request);
      onAfterSend(request,response);
      
    }catch(Throwable e) {
      onRequestError(request,response,e);
    }finally {
      onFinal(request,response);
    }
    //get body
    Map<String,Object> body = response.getBody();
    R r = wrapResponse((D)body.get("data"));
    r.setCode(MapUtils.getInteger(body, "code"));
    r.setMsg(MapUtils.getString(body, "msg"));
    return r;
  }
  
  /**
   * wrap response
   * @param data
   * @return
   */
  protected abstract R wrapResponse(D data);

  
  protected Map<String,Object> buildBody() {
    Map<String,Object> bodyMap = new HashMap<String, Object>(10);
    bodyMap.put("data", JsonUtils.toMap(this));
    bodyMap.put("type", getTypeCode());
    return bodyMap;
  }
  
  
  protected void onBeforeSend(RequestWrapper request,SdkContext context) {
    String type = getTypeCode();
    String privateKey = context.getPrivatekey();
    Map<String, Object> body = request.getBody();
    body.put("sign", SignUtils.sign(body,type,privateKey));
  }
  
  protected void onAfterSend(RequestWrapper request, ResponseWrapper response) {
    
  }
  
  protected void onFinal(RequestWrapper request, ResponseWrapper response) {
    Map<String,Object> replaceBody = new HashMap<String,Object>(10);
    Map<String,Object> body = response.getBody();
    Integer resultCode = (Integer)body.get("resultCode");
    if(resultCode != 0) {
      replaceBody.put("code", 500);
      replaceBody.put("msg", body.get("resultMessage"));
    }else {
      Map<String,Object> resultData = (Map<String,Object>)body.get("resultData");
      replaceBody.put("code", resultData.get("code"));
      replaceBody.put("msg", resultData.get("msg"));
      replaceBody.put("data", resultData.get("data"));
    }
    response.setBody(replaceBody);
  }

  protected void onRequestError(RequestWrapper request, ResponseWrapper response, Throwable e) {
    logger.error(e.getMessage(),e);
    if(response == null) {
      response = new ResponseWrapper();
    }
    response.addBody("resultCode", 500);
    response.addBody("resultMessage", e.getMessage());
  }
  
}
