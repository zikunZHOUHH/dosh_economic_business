<template>
  <div class="chat-container" :class="{ 'initial-state': !hasStarted }">
    <!-- Hero Section for Initial State -->
    <div class="hero-section" v-if="!hasStarted">
      <div class="hero-logo">Dosh AI</div>
      <p class="hero-subtitle">How can I help you today?</p>
    </div>

    <div class="messages" ref="messagesContainer" v-show="hasStarted">
      <div 
        v-for="(msg, index) in chatHistory" 
        :key="index" 
        :class="['message-row', msg.role]"
      >
        <div class="message-content">
          <div class="avatar" :class="msg.role">
            <span v-if="msg.role === 'user'">U</span>
            <span v-else>AI</span>
          </div>
          <div class="message-body">
             <div v-if="msg.images && msg.images.length" class="message-files">
               <div v-for="(img, i) in msg.images" :key="i" class="message-file">
                 <img :src="img" alt="Uploaded Image" class="history-image"/>
               </div>
             </div>
             <div v-if="msg.videos && msg.videos.length" class="message-files">
               <div v-for="(vid, i) in msg.videos" :key="i" class="message-file">
                 <video :src="vid" controls class="history-video"></video>
               </div>
             </div>
             <div class="text" v-html="renderMarkdown(msg.content)"></div>
          </div>
        </div>
      </div>
      
      <!-- Loading Indicator -->
      <div v-if="isLoading" class="message-row assistant">
        <div class="message-content">
          <div class="avatar assistant">
            <span>AI</span>
          </div>
          <div class="text">Thinking...</div>
        </div>
      </div>
    </div>

    <div class="input-area">
      <div class="input-box-wrapper">
        <div v-if="selectedFiles.length > 0" class="file-previews">
           <div v-for="(file, index) in selectedFiles" :key="index" class="file-preview-item">
             <img v-if="file.type === 'image'" :src="file.url" alt="Preview" />
             <div v-else class="video-icon">🎥</div>
             <button class="remove-file-btn" @click="removeFile(index)">×</button>
           </div>
        </div>
        <div class="input-box">
          <button class="attach-btn" @click="triggerFileInput" title="Attach files">
            <svg stroke="currentColor" fill="none" stroke-width="2" viewBox="0 0 24 24" stroke-linecap="round" stroke-linejoin="round" height="20" width="20" xmlns="http://www.w3.org/2000/svg"><path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"></path></svg>
          </button>
          <input 
            type="file" 
            ref="fileInput" 
            @change="handleFileSelect" 
            multiple 
            accept="image/*,video/*" 
            style="display: none;" 
          />
          <textarea 
            v-model="inputMessage" 
            @keydown.enter.prevent="sendMessage"
            placeholder="Send a message..."
            rows="1"
            ref="textarea"
            @input="autoResize"
          ></textarea>
          <button class="send-btn" @click="sendMessage" :disabled="isLoading || (!inputMessage.trim() && selectedFiles.length === 0)">
            <svg stroke="currentColor" fill="none" stroke-width="2" viewBox="0 0 24 24" stroke-linecap="round" stroke-linejoin="round" height="1em" width="1em" xmlns="http://www.w3.org/2000/svg"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, computed } from 'vue'
import axios from 'axios'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css' // Import a dark theme for code blocks

// State
const inputMessage = ref('')
const selectedFiles = ref([])
const fileInput = ref(null)
const chatHistory = ref([
  { role: 'assistant', content: 'Hello! I am Dosh AI. How can I help you today?' }
])
const isLoading = ref(false)
const messagesContainer = ref(null)
const textarea = ref(null)

// Computed
const hasStarted = computed(() => chatHistory.value.length > 1)

// Markdown Rendering
const renderMarkdown = (text) => {
  return marked(text)
}

// Highlight.js configuration for marked
marked.setOptions({
  highlight: function(code, lang) {
    const language = hljs.getLanguage(lang) ? lang : 'plaintext';
    return hljs.highlight(code, { language }).value;
  },
  langPrefix: 'hljs language-'
})

// Auto-resize textarea
const autoResize = () => {
  const el = textarea.value
  el.style.height = 'auto'
  el.style.height = el.scrollHeight + 'px'
}

// Scroll to bottom
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// File Upload Handlers
const triggerFileInput = () => {
  fileInput.value.click()
}

const handleFileSelect = (event) => {
  const files = Array.from(event.target.files)
  files.forEach(file => {
    // Avoid duplicates
    if (!selectedFiles.value.some(f => f.file.name === file.name && f.file.size === file.size)) {
       const url = URL.createObjectURL(file)
       const type = file.type.startsWith('video/') ? 'video' : 'image'
       selectedFiles.value.push({ file, url, type })
    }
  })
  // Reset input so same file can be selected again if needed (though we check duplicates)
  event.target.value = ''
  nextTick(() => autoResize()) // Resize in case previews change layout height? Not strictly needed for textarea but good for container
}

const removeFile = (index) => {
  URL.revokeObjectURL(selectedFiles.value[index].url)
  selectedFiles.value.splice(index, 1)
}

const fileToBase64 = (file) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.readAsDataURL(file)
    reader.onload = () => resolve(reader.result)
    reader.onerror = error => reject(error)
  })
}

// Send Message
const sendMessage = async () => {
  if ((!inputMessage.value.trim() && selectedFiles.value.length === 0) || isLoading.value) return

  const userMsg = inputMessage.value.trim()
  const filesToSend = [...selectedFiles.value]
  
  // Add user message to history with images for immediate display
  // We need to store images in history as URLs for display
  const historyEntry = { 
    role: 'user', 
    content: userMsg,
    images: filesToSend.filter(f => f.type === 'image').map(f => f.url),
    videos: filesToSend.filter(f => f.type === 'video').map(f => f.url)
  }
  
  chatHistory.value.push(historyEntry)
  
  // Clear input
  inputMessage.value = ''
  selectedFiles.value = [] // Clear selected files UI
  autoResize() // Reset height
  scrollToBottom()

  // Prepare assistant placeholder
  const assistantMsgIndex = chatHistory.value.push({ role: 'assistant', content: '' }) - 1
  isLoading.value = true

  try {
    // Process files: Images to Base64, Videos uploaded to server
    const imagesBase64 = []
    const videoPaths = []
    
    const uploadVideo = async (file) => {
       const formData = new FormData()
       formData.append('file', file)
       const res = await fetch('/api/video/upload', {
         method: 'POST',
         body: formData
       })
       if (!res.ok) throw new Error('Video upload failed')
       const data = await res.json()
       return data.path
    }

    // Update assistant message to show upload progress if needed
    if (filesToSend.some(f => f.type === 'video')) {
        chatHistory.value[assistantMsgIndex].content = 'Uploading videos...'
    }

    for (const f of filesToSend) {
      if (f.type === 'image') {
        const b64 = await fileToBase64(f.file)
        imagesBase64.push(b64)
      } else if (f.type === 'video') {
        try {
           const path = await uploadVideo(f.file)
           videoPaths.push(path)
        } catch (e) {
           console.error('Upload error:', e)
           chatHistory.value[assistantMsgIndex].content = `Error: Failed to upload video ${f.file.name}`
           isLoading.value = false
           return
        }
      }
    }
    
    // Clear "Uploading..." message if it was set
    if (videoPaths.length > 0) {
        chatHistory.value[assistantMsgIndex].content = ''
    }

    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ 
        message: userMsg,
        images: imagesBase64,
        videoPaths: videoPaths
      })
    })

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value, { stream: true })
      buffer += chunk
      
      const lines = buffer.split('\n\n')
      buffer = lines.pop()

      for (const line of lines) {
        if (line.startsWith('data:')) {
          // Handle multi-line data where Spring SseEmitter splits content with newlines into multiple "data:" lines
          const data = line.replace(/^data:/, '').replace(/[\r\n]+data:/g, '\n')
          if (data) {
             chatHistory.value[assistantMsgIndex].content += data
             scrollToBottom()
          }
        }
      }
    }

  } catch (error) {
    console.error('API Error:', error)
    chatHistory.value[assistantMsgIndex].content += '\n[Error: Failed to get response]'
  } finally {
    isLoading.value = false
    scrollToBottom()
  }
}
</script>