# Postman 测试指南 - Dosh ChatGLM 集成

本指南用于测试 Dosh 项目集成的 ChatGLM 聊天接口。

## 1. 前置准备

在进行测试之前，请确保完成以下步骤：

1.  **配置 API Key**：
    打开 `src/main/resources/application.properties`，找到 `chatglm.api.key`，填入你真实的智谱 AI API Key。
    ```properties
    chatglm.api.key=YOUR_ACTUAL_API_KEY_HERE
    ```

2.  **配置意图识别 Provider**:
    打开 `src/main/resources/application.properties`，确保以下配置（默认即为 ChatGLM）：
    ```properties
    ai.intent.provider=chatglm
    ```
    *在此模式下，不需要启动 Python Mock Server。*

3.  **启动 Spring Boot 应用**：
    运行 `DoshApplication` 主类。

---

## 2. Postman 请求结构

### 基础信息
- **请求方法**: `POST`
- **请求 URL**: `http://localhost:8080/api/chat/send`
- **Content-Type**: `application/json`

### 请求体 (Body) - JSON 格式
```json
{
    "message": "你好，请用一句话介绍你自己"
}
```

### 预期响应 (Response)

**成功情况 (ChatGLM 回复)**:
```json
{
    "code": 0,
    "message": "ok",
    "data": "你好！我是智谱AI，由清华大学计算机系技术成果转化而来的公司，致力于打造新一代认知智能通用模型。"
}
```

**意图识别拦截情况 (例如输入 'draw a cat')**:
```json
{
    "code": 0,
    "message": "ok",
    "data": "[Image Generation Task] draw a cat"
}
```

---

## 3. 流式响应测试 (Streaming)

Postman 支持 Server-Sent Events (SSE) 的测试。

### 请求结构
- **请求方法**: `POST`
- **请求 URL**: `http://localhost:8080/api/chat/stream`
- **Content-Type**: `application/json`
- **Headers**:
    - `Accept`: `text/event-stream`

### 请求体 (Body) - JSON 格式
```json
{
    "message": "请讲一个长故事"
}
```

### 预期效果
Postman 不会像普通请求那样直接显示 JSON，而是会在 Response 区域持续显示接收到的数据流 (Stream)，每行以 `data:` 开头。

---

## 4. 多模态文件上传测试 (Multi-modal)

该项目支持在对话中附带图片或视频文件（通过 Base64 编码传输）。

### 请求结构
- **请求方法**: `POST`
- **请求 URL**: `http://localhost:8080/api/chat/stream` (支持流式) 或 `/api/chat/send`
- **Content-Type**: `application/json`

### 请求体 (Body) - JSON 格式
```json
{
    "message": "这张图片里有什么？",
    "images": [
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    ],
    "videos": []
}
```
*注：实际测试时请使用真实的 Base64 字符串替换示例中的短字符串。*

---

## 5. 测试场景建议

| 测试场景 | 输入 Message (Body) | 预期行为 | 预期 Data 输出特征 |
| :--- | :--- | :--- | :--- |
| **普通对话** | `"请写一首关于春天的诗"` | 路由至 ChatGLM (Chat) | 包含诗歌内容的文本 |
| **知识问答** | `"Java 的 HashMap 是线程安全的吗？"` | 路由至 ChatGLM (Chat) | 解释 HashMap 非线程安全 |
| **意图识别-画图** | `"Draw a futuristic city"` | 路由至 ImageService | `[Image Generation Task] ...` |
| **意图识别-视频** | `"Generate a video of ocean waves"` | 路由至 VideoService | `[Video Generation Task] ...` |
| **带图对话** | `"描述这张图片" + images[]` | 后端接收图片并处理 | 目前后端仅日志记录图片，返回文本回复 |

## 6. 常见问题排查

- **报错 500 (Internal Server Error)**:
  - 检查 `application.properties` 中的 ChatGLM API Key 是否正确。
  - 检查网络是否能访问 `open.bigmodel.cn`。
- **报错 413 (Payload Too Large)**:
  - Base64 图片过大可能导致请求体超过服务器限制。Spring Boot 默认限制可能需要调整（如需支持大图）。
