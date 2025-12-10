package com.ark.example;
   import com.volcengine.ark.runtime.model.responses.content.InputContentItemImage;
   import com.volcengine.ark.runtime.model.responses.content.InputContentItemText;
   import com.volcengine.ark.runtime.model.responses.content.InputContentItemVideo;
   import com.volcengine.ark.runtime.model.responses.item.ItemEasyMessage;
   import com.volcengine.ark.runtime.service.ArkService;
   import com.volcengine.ark.runtime.model.responses.request.*;
   import com.volcengine.ark.runtime.model.responses.response.ResponseObject;
   import com.volcengine.ark.runtime.model.responses.constant.ResponsesConstants;
   import com.volcengine.ark.runtime.model.responses.item.MessageContent;
   import java.nio.file.Files;
   import java.nio.file.Paths;
   import java.util.Base64;
   import java.io.IOException;
   
   public class demo {
       private static String encodeFile(String filePath) throws IOException {
           byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
           return Base64.getEncoder().encodeToString(fileBytes);
       }
       public static void main(String[] args) {
           String apiKey = System.getenv("ARK_API_KEY");
           ArkService arkService = ArkService.builder().apiKey(apiKey).baseUrl("https://ark.cn-beijing.volces.com/api/v3").build();
           // Convert local files to Base64-encoded strings.
           String base64Data = "";
           try {
               base64Data = "data:video/mp4;base64," + encodeFile("/Users/demo.mp4");
           } catch (IOException e) {
               System.err.println("编码失败: " + e.getMessage());
           }
           CreateResponsesRequest request = CreateResponsesRequest.builder()
                   .model("doubao-seed-1-6-251015")
                   .input(ResponsesInput.builder().addListItem(
                           ItemEasyMessage.builder().role(ResponsesConstants.MESSAGE_ROLE_USER).content(
                                   MessageContent.builder()
                                           .addListItem(InputContentItemVideo.builder().videoUrl(base64Data).fps(2F).build())
                                           .build()
                           ).build()
                   ).build())
                   .build();
           ResponseObject resp = arkService.createResponse(request);
           System.out.println(resp);
   
           arkService.shutdownExecutor();
       }
   }