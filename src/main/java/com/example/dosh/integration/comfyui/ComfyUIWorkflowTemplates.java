package com.example.dosh.integration.comfyui;

public class                           ComfyUIWorkflowTemplates {
    public static final String WAN_2_1_WORKFLOW = """
{
  "75": {
    "class_type": "UNETLoader",
    "inputs": {
      "unet_name": "wan2.2_i2v_high_noise_14B_fp16.safetensors",
      "weight_dtype": "default"
    }
  },
  "83": {
    "class_type": "LoraLoaderModelOnly",
    "inputs": {
      "model": ["75", 0],
      "lora_name": "wan2.2_i2v_lightx2v_4steps_lora_v1_high_noise.safetensors",
      "strength_model": 1.0
    }
  },
  "76": {
    "class_type": "UNETLoader",
    "inputs": {
      "unet_name": "wan2.2_i2v_low_noise_14B_fp16.safetensors",
      "weight_dtype": "default"
    }
  },
  "85": {
    "class_type": "LoraLoaderModelOnly",
    "inputs": {
      "model": ["76", 0],
      "lora_name": "wan2.2_i2v_lightx2v_4steps_lora_v1_low_noise.safetensors",
      "strength_model": 1.0
    }
  },
  "82": {
    "class_type": "ModelSamplingSD3",
    "inputs": {
      "model": ["83", 0],
      "shift": 5
    }
  },
  "86": {
    "class_type": "ModelSamplingSD3",
    "inputs": {
      "model": ["85", 0],
      "shift": 5
    }
  },
  "71": {
    "class_type": "CLIPLoader",
    "inputs": {
      "clip_name": "umt5_xxl_fp8_e4m3fn_scaled.safetensors",
      "type": "wan",
      "device": "default"
    }
  },
  "89": {
    "class_type": "CLIPTextEncode",
    "inputs": {
      "clip": ["71", 0],
      "text": "%POSITIVE_PROMPT%"
    }
  },
  "72": {
    "class_type": "CLIPTextEncode",
    "inputs": {
      "clip": ["71", 0],
      "text": ""
    }
  },
  "73": {
    "class_type": "VAELoader",
    "inputs": {
      "vae_name": "wan_2.1_vae.safetensors"
    }
  },
  "74": {
    "class_type": "EmptyHunyuanLatentVideo",
    "inputs": {
      "width": 1280,
      "height": 720,
      "length": 1,
      "batch_size": 1
    }
  },
  "81": {
    "class_type": "KSamplerAdvanced",
    "inputs": {
      "model": ["82", 0],
      "positive": ["89", 0],
      "negative": ["72", 0],
      "latent_image": ["74", 0],
      "add_noise": "enable",
      "noise_seed": 342386651972596,
      "steps": 4,
      "cfg": 1,
      "sampler_name": "euler",
      "scheduler": "simple",
      "start_at_step": 0,
      "end_at_step": 2,
      "return_with_leftover_noise": "enable"
    }
  },
  "78": {
    "class_type": "KSamplerAdvanced",
    "inputs": {
      "model": ["86", 0],
      "positive": ["89", 0],
      "negative": ["72", 0],
      "latent_image": ["81", 0],
      "add_noise": "disable",
      "noise_seed": 0,
      "steps": 4,
      "cfg": 1,
      "sampler_name": "euler",
      "scheduler": "simple",
      "start_at_step": 2,
      "end_at_step": 4,
      "return_with_leftover_noise": "disable"
    }
  },
  "87": {
    "class_type": "VAEDecode",
    "inputs": {
      "samples": ["78", 0],
      "vae": ["73", 0]
    }
  },
  "118": {
    "class_type": "SaveImage",
    "inputs": {
      "filename_prefix": "dosh_wan",
      "images": ["87", 0]
    }
  }
}
""";
}
