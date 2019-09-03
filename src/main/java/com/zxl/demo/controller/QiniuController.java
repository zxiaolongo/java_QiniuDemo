package com.zxl.demo.controller;

import com.google.gson.Gson;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import com.qiniu.util.StringUtils;
import com.qiniu.util.UrlSafeBase64;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("qiniu")
public class QiniuController {
    //notice 1普通Token生成
    @RequestMapping("/getToken")
    public String getQiniuToken(){
        String accessKey = "VAMWbFMtBwoy9lvRFAIx1uhYHyFSZns948oiOTOm";
        String secretKey = "CG46VAKuQouqv6A_nBYYtrb1IcZK5kBBab3yjxdn";
        String bucket = "zhangxiaolong";
        Auth auth = Auth.create(accessKey, secretKey);
        return auth.uploadToken(bucket);
    }

    /**
     * notice 2 覆盖上传Token生成
     * @param key 进行覆盖的key
     * @return token
     */
    public String getCoveredToken(String key){
        String accessKey = "VAMWbFMtBwoy9lvRFAIx1uhYHyFSZns948oiOTOm";
        String secretKey = "CG46VAKuQouqv6A_nBYYtrb1IcZK5kBBab3yjxdn";
        String bucket = "zhangxiaolong";
        Auth auth = Auth.create(accessKey, secretKey);
        return auth.uploadToken(bucket,key);
    }

    /**
     * notice 3 设置固定的返回格式和token 时效
     */
    //则文件上传到七牛之后，收到的回复内容如下：{"key":"qiniu.jpg","hash":"Ftgm-CkWePC9fzMBTRNmPMhGBcSV","bucket":"if-bc","fsize":39335}
    public String setResultToken(){
        String accessKey = "access key";
        String secretKey = "secret key";
        String bucket = "bucket name";
        Auth auth = Auth.create(accessKey, secretKey);
        StringMap putPolicy = new StringMap();
        putPolicy.put("returnBody", "{\"key\":\"$(key)\",\"hash\":\"$(etag)\",\"bucket\":\"$(bucket)\",\"fsize\":$(fsize)}");
        long expireSeconds = 3600;//有效时间 单位s
        return auth.uploadToken(bucket, null, expireSeconds, putPolicy);
    }

    /**
     * notice 4带回调业务服务器回调的token
     */
    //上面生成的自定义上传回复的上传凭证适用于上传端（无论是客户端还是服务端）和七牛服务器之间进行直接交互的情况下。
    // 在客户端上传的场景之下，有时候客户端需要在文件上传到七牛之后，
    // 从业务服务器获取相关的信息，这个时候就要用到七牛的上传回调及相关回调参数的设置。
    public String getServerToken(){
        String accessKey = "access key";
        String secretKey = "secret key";
        String bucket = "bucket name";

        Auth auth = Auth.create(accessKey, secretKey);
        StringMap putPolicy = new StringMap();
        putPolicy.put("callbackUrl", "http://api.example.com/qiniu/upload/callback");
        putPolicy.put("callbackBody", "{\"key\":\"$(key)\",\"hash\":\"$(etag)\",\"bucket\":\"$(bucket)\",\"fsize\":$(fsize)}");
        putPolicy.put("callbackBodyType", "application/json");
        long expireSeconds = 3600;
        return auth.uploadToken(bucket, null, expireSeconds, putPolicy);
    }
    /**
     * notice 5 在使用了上传回调的情况下，客户端收到的回复就是业务服务器响应七牛的JSON格式内容。
     */
    //上面生成的自定义上传回复的上传凭证适用于上传端（无论是客户端还是服务端）和七牛服务器之间进行直接交互的情况下。在客户端上传的场景之下，
    // 有时候客户端需要在文件上传到七牛之后，从业务服务器获取相关的信息，这个时候就要用到七牛的上传回调及相关回调参数的设置。
    public String get(){
        String accessKey = "access key";
        String secretKey = "secret key";
        String bucket = "bucket name";

        Auth auth = Auth.create(accessKey, secretKey);
        StringMap putPolicy = new StringMap();
        putPolicy.put("callbackUrl", "http://api.example.com/qiniu/upload/callback");
        putPolicy.put("callbackBody", "key=$(key)&hash=$(etag)&bucket=$(bucket)&fsize=$(fsize)");
        long expireSeconds = 3600;
        return auth.uploadToken(bucket, null, expireSeconds, putPolicy);
    }

    /**
     * notice 6
     */
    //带数据处理的凭证
    //七牛支持在文件上传到七牛之后，立即对其进行多种指令的数据处理，这个只需要在生成的上传凭证中指定相关的处理参数即可。
    public String getDealToken(){
        String accessKey = "access key";
        String secretKey = "secret key";
        String bucket = "bucket name";

        Auth auth = Auth.create(accessKey, secretKey);
        StringMap putPolicy = new StringMap();
//数据处理指令，支持多个指令
        String saveMp4Entry = String.format("%s:avthumb_test_target.mp4", bucket);
        String saveJpgEntry = String.format("%s:vframe_test_target.jpg", bucket);
        String avthumbMp4Fop = String.format("avthumb/mp4|saveas/%s", UrlSafeBase64.encodeToString(saveMp4Entry));
        String vframeJpgFop = String.format("vframe/jpg/offset/1|saveas/%s", UrlSafeBase64.encodeToString(saveJpgEntry));
//将多个数据处理指令拼接起来
        String persistentOpfs = StringUtils.join(new String[]{
                avthumbMp4Fop, vframeJpgFop
        }, ";");
        putPolicy.put("persistentOps", persistentOpfs);
//数据处理队列名称，必填
        putPolicy.put("persistentPipeline", "mps-pipe1");
//数据处理完成结果通知地址
        putPolicy.put("persistentNotifyUrl", "http://api.example.com/qiniu/pfop/notify");

        long expireSeconds = 3600;
        return auth.uploadToken(bucket, null, expireSeconds, putPolicy);
    }

    /**
     * notice 7
     */
  //文件上传   注意
    //若不指定 Region 或 Region.autoRegion() ，则会使用 自动判断 区域，使用相应域名处理。
    //如果可以明确 区域 的话，最好指定固定区域，这样可以少一步网络请求，少一步出错的可能。
   //最简单的就是上传本地文件，直接指定文件的完整路径即可上传。
    public void get2(){
//构造一个带指定 Region 对象的配置类
        Configuration cfg = new Configuration(Region.region0());
//...其他参数参考类注释

        UploadManager uploadManager = new UploadManager(cfg);
//...生成上传凭证，然后准备上传
        String accessKey = "your access key";
        String secretKey = "your secret key";
        String bucket = "your bucket name";
//如果是Windows情况下，格式是 D:\\qiniu\\test.png
        String localFilePath = "/home/qiniu/test.png";
//默认不指定key的情况下，以文件内容的hash值作为文件名
        String key = null;

        Auth auth = Auth.create(accessKey, secretKey);
        String upToken = auth.uploadToken(bucket);

        try {
            Response response = uploadManager.put(localFilePath, key, upToken);
            //解析上传成功的结果
            DefaultPutRet putRet = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);
            System.out.println(putRet.key);
            System.out.println(putRet.hash);
        } catch (QiniuException ex) {
            Response r = ex.response;
            System.err.println(r.toString());
            try {
                System.err.println(r.bodyString());
            } catch (QiniuException ex2) {
                //ignore
            }
        }

    }

}
