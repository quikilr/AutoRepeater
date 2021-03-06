package burp;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

class Replacement {

  public static final String[] REPLACEMENT_TYPE_OPTIONS = {
      "Request Header",
      "Request Body",
      "Request Param Name",
      "Request Param Value",
      "Request Cookie Name",
      "Request Cookie Value",
      "Request First Line",
      "Request String",

      "Remove Parameter By Name",
      "Remove Parameter By Value",
      "Remove Cookie By Name",
      "Remove Cookie By Value"
      //"Remove Header By Name",
      //"Remove Header By Value"
  };

  public static final String[] REPLACEMENT_COUNT_OPTINONS = {
      "Replace First",
      "Replace All"
  };

  private enum MatchAndReplaceType {
    MATCH_NAME_REPLACE_NAME,
    MATCH_NAME_REPLACE_VALUE,
    MATCH_VALUE_REPLACE_VALUE,
    MATCH_VALUE_REPLACE_NAME,
    MATCH_NAME_REMOVE,
    MATCH_VALUE_REMOVE
  }

  private String type;
  private String match;
  private String replace;
  private String comment;
  private String which;
  private Boolean isRegexMatch;
  private Boolean isEnabled;

  public Replacement(
      String type,
      String match,
      String replace,
      String which,
      String comment,
      boolean isRegexMatch) {
    this.type = type;
    this.match = match;
    this.replace = replace;
    this.which = which;
    this.comment = comment;
    this.isRegexMatch = isRegexMatch;
    this.isEnabled = true;
  }

  private byte[] updateBurpParamName(
      byte[] request,
      int parameterType,
      MatchAndReplaceType matchAndReplaceType) {
    IExtensionHelpers helpers = BurpExtender.getHelpers();
    IRequestInfo analyzedRequest = helpers.analyzeRequest(request);
    List<IParameter> parameters = analyzedRequest.getParameters();
    List<IParameter> originalParameters = analyzedRequest.getParameters();
    boolean wasChanged = false;
    for (ListIterator<IParameter> iterator = parameters.listIterator(); iterator.hasNext(); ) {
      int i = iterator.nextIndex();
      IParameter currentParameter = iterator.next();
      if (currentParameter.getType() == parameterType) {
        switch (matchAndReplaceType) {
          case MATCH_NAME_REPLACE_NAME:
            // Each if statement checks if isRegexMatch && check regex
            // || regular string compare
            if ((this.isRegexMatch && currentParameter.getName().matches(this.match))
                || currentParameter.getName().equals(this.match)) {
              parameters.set(i, helpers.buildParameter(
                  this.replace,
                  currentParameter.getValue(),
                  currentParameter.getType()));
              wasChanged = true;
            }
            break;
          case MATCH_NAME_REPLACE_VALUE:
            if ((this.isRegexMatch && currentParameter.getName().matches(this.match))
                || currentParameter.getName().equals(this.match)) {
              parameters.set(i, helpers.buildParameter(
                  currentParameter.getName(),
                  this.replace,
                  currentParameter.getType()));
              wasChanged = true;
            }
            break;
          case MATCH_VALUE_REPLACE_VALUE:
            if ((this.isRegexMatch && currentParameter.getValue().matches(this.match))
                || currentParameter.getValue().equals(this.match)) {
              parameters.set(i, helpers.buildParameter(
                  currentParameter.getName(),
                  this.replace,
                  currentParameter.getType()));
              wasChanged = true;
            }
            break;
          case MATCH_VALUE_REPLACE_NAME:
            if ((this.isRegexMatch && currentParameter.getValue().matches(this.match))
                || currentParameter.getValue().equals(this.match)) {
              parameters.set(i, helpers.buildParameter(
                  currentParameter.getName(),
                  this.replace,
                  currentParameter.getType()));
              wasChanged = true;
            }
            break;
          case MATCH_NAME_REMOVE:
            if ((this.isRegexMatch && currentParameter.getName().matches(this.match))
                || currentParameter.getName().equals(this.match)) {
              iterator.remove();
              wasChanged = true;
            }
            break;
          case MATCH_VALUE_REMOVE:
            if ((this.isRegexMatch && currentParameter.getValue().matches(this.match))
                || currentParameter.getValue().equals(this.match)) {
              iterator.remove();
              wasChanged = true;
            }
            break;
          default:
            break;
        }
      }
      // Bail if anything was changed
      if (this.which.equals("Replace First")) {
        if (wasChanged) {
          break;
        }
      }
    }
    if (wasChanged) {
      byte[] tempRequest = Arrays.copyOf(request, request.length);
      // Remove every parameter
      for (IParameter param : originalParameters) {
        tempRequest = helpers.removeParameter(tempRequest, param);
      }
      // Add them back
      for (IParameter param : parameters) {
        tempRequest = helpers.addParameter(tempRequest, param);
      }
      // Update the body and headers
      IRequestInfo tempAnalyzedRequest = helpers.analyzeRequest(tempRequest);
      byte[] body = Arrays
          .copyOfRange(tempRequest, tempAnalyzedRequest.getBodyOffset(), tempRequest.length);
      List<String> headers = tempAnalyzedRequest.getHeaders();
      return helpers.buildHttpMessage(headers, body);
    }
    // Return the modified request
    return request;
  }

  // This is a hack around binary content causing requests to send
  private byte[] updateContent(byte[] request) {
    String requestString;
    String originalRequestString;

    try {
      requestString = new String(request, "UTF-8");
      originalRequestString = requestString;
    } catch (UnsupportedEncodingException e) {
      return request;
    }

    if (this.which.equals("Replace First")) {
      requestString = requestString.replaceFirst(this.match, this.replace);
    } else {
      requestString = requestString.replaceAll(this.match, this.replace);
    }

    if (!requestString.equals(originalRequestString)) {
      return requestString.getBytes();
    } else {
      return request;
    }
  }

  private boolean replaceFirst() {
    return this.which.equals("Replace First");
  }

  private byte[] updateHeader(byte[] request) {
    IExtensionHelpers helpers = BurpExtender.getHelpers();
    IRequestInfo analyzedRequest = helpers.analyzeRequest(request);
    List<String> headers = analyzedRequest.getHeaders();
    byte[] body = Arrays.copyOfRange(request, analyzedRequest.getBodyOffset(), request.length);
    ArrayList<String> newHeaders = new ArrayList<>();
    boolean wasChanged = false;
    for (String header : headers) {
      if (!replaceFirst() || (replaceFirst() && !wasChanged)) {
        if (this.isRegexMatch) {
          if (header.matches(this.match)) {
            header = this.replace;
            wasChanged = true;
          }
        } else {
          if (header.equals(this.match)) {
            header = this.replace;
            wasChanged = true;
          }
        }
      }
      // Don't add empty headers, they mess things up
      if (!header.equals("")) {
        newHeaders.add(header);
      }
    }
    return helpers.buildHttpMessage(newHeaders, body);
  }

  private byte[] updateRequestBody(byte[] request) {
    IExtensionHelpers helpers = BurpExtender.getHelpers();
    IRequestInfo analyzedRequest = helpers.analyzeRequest(request);
    List<String> headers = analyzedRequest.getHeaders();
    byte[] body = Arrays.copyOfRange(request, analyzedRequest.getBodyOffset(), request.length);
    boolean wasChanged = false;
    String bodyString;

    try {
      bodyString = new String(body, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      return request;
    }

    if (this.isRegexMatch) {
      if (bodyString.matches(this.match)) {
        body = this.replace.getBytes();
        wasChanged = true;
      }
    } else {
      if (bodyString.equals(this.match)) {
        body = bodyString.replace(this.match, this.replace).getBytes();
        wasChanged = true;
      }
    }

    // This helps deal with binary data getting messed up from the string conversion and causing a new request.
    if (wasChanged) {
      return helpers.buildHttpMessage(headers, body);
    } else {
      return request;
    }
  }

  private byte[] updateRequestParamName(byte[] request) {
    request = updateBurpParamName(request, IParameter.PARAM_BODY,
        MatchAndReplaceType.MATCH_NAME_REPLACE_NAME);
    return updateBurpParamName(request, IParameter.PARAM_URL,
        MatchAndReplaceType.MATCH_NAME_REPLACE_NAME);
  }

  private byte[] updateRequestParamValue(byte[] request) {
    request = updateBurpParamName(request, IParameter.PARAM_BODY,
        MatchAndReplaceType.MATCH_VALUE_REPLACE_VALUE);
    return updateBurpParamName(request, IParameter.PARAM_URL,
        MatchAndReplaceType.MATCH_VALUE_REPLACE_VALUE);
  }

  private byte[] updateCookieName(byte[] request) {
    return updateBurpParamName(request, IParameter.PARAM_COOKIE,
        MatchAndReplaceType.MATCH_NAME_REPLACE_NAME);
  }

  private byte[] updateCookieValue(byte[] request) {
    return updateBurpParamName(request, IParameter.PARAM_COOKIE,
        MatchAndReplaceType.MATCH_VALUE_REPLACE_VALUE);
  }

  private byte[] removeParameterByName(byte[] request) {
    request = updateBurpParamName(request, IParameter.PARAM_BODY,
        MatchAndReplaceType.MATCH_NAME_REMOVE);
    return updateBurpParamName(request, IParameter.PARAM_URL,
        MatchAndReplaceType.MATCH_NAME_REMOVE);
  }

  private byte[] removeParameterByValue(byte[] request) {
    request = updateBurpParamName(request, IParameter.PARAM_BODY,
        MatchAndReplaceType.MATCH_VALUE_REMOVE);
    return updateBurpParamName(request, IParameter.PARAM_URL,
        MatchAndReplaceType.MATCH_VALUE_REMOVE);
  }

  private byte[] removeCookieByName(byte[] request) {
    return updateBurpParamName(request, IParameter.PARAM_COOKIE,
        MatchAndReplaceType.MATCH_NAME_REMOVE);
  }

  private byte[] removeCookieByValue(byte[] request) {
    return updateBurpParamName(request, IParameter.PARAM_COOKIE,
        MatchAndReplaceType.MATCH_VALUE_REMOVE);
  }

  private byte[] updateRequestFirstLine(byte[] request) {
    IExtensionHelpers helpers = BurpExtender.getHelpers();
    IRequestInfo analyzedRequest = helpers.analyzeRequest(request);
    List<String> headers = analyzedRequest.getHeaders();
    byte[] body = Arrays.copyOfRange(request, analyzedRequest.getBodyOffset(), request.length);
    String firstRequestString = headers.get(0);
    if (replaceFirst()) {
      headers.set(0, firstRequestString.replaceFirst(this.match, this.replace));
    } else {
      headers.set(0, firstRequestString.replaceAll(this.match, this.replace));
    }
    return helpers.buildHttpMessage(headers, body);
  }

  public byte[] performReplacement(IHttpRequestResponse messageInfo) {
    byte[] request = messageInfo.getRequest();

    if (this.isEnabled) {
      switch (this.type) {
        case ("Request Header"):
          return updateHeader(request);
        case ("Request Body"):
          return updateRequestBody(request);
        case ("Request Param Name"):
          return updateRequestParamName(request);
        case ("Request Param Value"):
          return updateRequestParamValue(request);
        case ("Request Cookie Name"):
          return updateCookieName(request);
        case ("Request Cookie Value"):
          return updateCookieValue(request);
        case ("Request First Line"):
          return updateRequestFirstLine(request);
        case ("Request String"):
          return updateContent(request);
        case ("Remove Parameter By Name"):
          return removeParameterByName(request);
        case ("Remove Parameter By Value"):
          return removeParameterByValue(request);
        case ("Remove Cookie By Name"):
          return removeCookieByName(request);
        case ("Remove Cookie By Value"):
          return removeCookieByValue(request);
        default:
          return request;
      }
    }
    return request;
  }

  public String getType() {
    return type;
  }

  public String getMatch() {
    return match;
  }

  public String getReplace() {
    return replace;
  }

  public String getComment() {
    return comment;
  }

  public boolean isRegexMatch() {
    return isRegexMatch;
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  public void setType(String type) {
    this.type = type;
  }

  public void setMatch(String match) {
    this.match = match;
  }

  public void setReplace(String replace) {
    this.replace = replace;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public void setRegexMatch(Boolean regexMatch) {
    isRegexMatch = regexMatch;
  }

  public void setEnabled(Boolean enabled) {
    isEnabled = enabled;
  }

  public String getWhich() {
    return which;
  }

  public void setWhich(String which) {
    this.which = which;
  }

}
