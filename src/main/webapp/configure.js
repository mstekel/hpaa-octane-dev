function load(a,path){
    var buttonStatus = false;
    if(buttonStatus) return;
    buttonStatus = true;
    var mcUserName = document.getElementsByName("runfromfs.fsUserName")[0].value;
    var mcPassword = document.getElementsByName("runfromfs.fsPassword")[0].value;
    var mcUrl = document.getElementsByName("runfromfs.mcServerName")[0].value;
    var useProxy = document.getElementsByName("proxySettings")[0].checked;
    var proxyAddress = document.getElementsByName("runfromfs.fsProxyAddress")[0].value;
    var useAuthentication = document.getElementsByName("runfromfs.fsUseAuthentication")[0].checked;
    var proxyUserName = document.getElementsByName("runfromfs.fsProxyUserName")[0].value;
    var proxyPassword = document.getElementsByName("runfromfs.fsProxyPassword")[0].value;
    var baseUrl = "";
    if(mcUserName == '' || mcPassword == ''|| (useProxy && proxyAddress == '') || (useAuthentication && (proxyUserName == '' || proxyPassword == ''))){
        document.getElementById("errorMessage").style.display = "block";
        buttonStatus = false;
        return;
    }
    var previousJobId = document.getElementsByName("runfromfs.fsJobId")[0].value;
    a.getMcServerUrl(mcUrl, function(r){
        baseUrl = r.responseObject();
        a.getJobId(baseUrl,mcUserName, mcPassword, proxyAddress, proxyUserName, proxyPassword, previousJobId, function (response) {
            var jobResponse = response.responseObject();
            if(jobResponse == null){
                document.getElementById("errorMessage").style.display = "block";
                buttonStatus = false;
                return;
            }
            var openedWindow = window.open(baseUrl+path+jobResponse+'&displayUFTMode=true','test parameters','height=820','width=1130');
            var messageCallBack = function (event) {
                if (event && event.data && event.data=="mcCloseWizard") {
                    a.populateAppAndDevice(baseUrl,mcUserName,mcPassword,proxyAddress, proxyUserName, proxyPassword,jobResponse, function (app) {
                        var jobInfo = app.responseObject();
                        var deviceId = "";
                        var OS = "";
                        var manufacturerAndModel = "";
                        var targetLab = "";
                        if(jobInfo['deviceJSON']){
                            if(jobInfo['deviceJSON']['deviceId']){
                                deviceId = jobInfo['deviceJSON']['deviceId'];
                            }
                            if(jobInfo['deviceJSON']['OS']){
                                OS = jobInfo['deviceJSON']['OS'];
                            }
                            if(jobInfo['deviceJSON']['manufacturerAndModel']){
                                manufacturerAndModel = jobInfo['deviceJSON']['manufacturerAndModel'];
                            }
                        }
                        if(jobInfo['deviceCapability']){
                            if(jobInfo['deviceCapability']['OS']){
                                OS = jobInfo['deviceCapability']['OS'];
                            }
                            if(jobInfo['deviceCapability']['manufacturerAndModel']){
                                manufacturerAndModel = jobInfo['deviceCapability']['manufacturerAndModel'];
                            }
                            if(jobInfo['deviceCapability']['targetLab']){
                                targetLab = jobInfo['deviceCapability']['targetLab'];
                            }
                        }
                        document.getElementsByName("runfromfs.fsDeviceId")[0].value = deviceId;
                        document.getElementsByName("runfromfs.fsOs")[0].value = OS;
                        document.getElementsByName("runfromfs.fsManufacturerAndModel")[0].value = manufacturerAndModel;
                        document.getElementsByName("runfromfs.fsTargetLab")[0].value = targetLab;
                        document.getElementsByName("runfromfs.fsLaunchAppName")[0].value = jobInfo['definitions']['launchApplicationName'];
                        document.getElementsByName("runfromfs.fsInstrumented")[0].value = jobInfo['definitions']['instrumented'];
                        document.getElementsByName("runfromfs.fsAutActions")[0].value = jobInfo['definitions']['autActions'];
                        document.getElementsByName("runfromfs.fsDevicesMetrics")[0].value = jobInfo['definitions']['deviceMetrics'];
                        document.getElementsByName("runfromfs.fsExtraApps")[0].value = jobInfo['extraApps'];
                        document.getElementsByName("runfromfs.fsJobId")[0].value = jobInfo['jobUUID'];
                        buttonStatus = false;
                        document.getElementById("errorMessage").style.display = "none";
                        window.removeEventListener("message",messageCallBack, false);
                        openedWindow.close();
                    });
                }
            };
            window.addEventListener("message", messageCallBack ,false);
            function checkChild() {
                if (openedWindow && openedWindow.closed) {
                    clearInterval(timer);
                    buttonStatus = false;
                }
            }
            var timer = setInterval(checkChild, 500);
        });
    });

}