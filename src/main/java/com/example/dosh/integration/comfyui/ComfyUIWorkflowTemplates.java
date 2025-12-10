package com.example.dosh.integration.comfyui;

public class                           ComfyUIWorkflowTemplates {
    public static final String WAN_2_1_WORKFLOW = """
{
    "48": {
      "class_type": "Flux2Scheduler",
      "inputs": {
        "steps": 20,
        "width": 1248,
        "height": 832
      }
    },

    "22": {
      "class_type": "BasicGuider",
      "inputs": {
        "model": ["12", 0],
        "conditioning": ["26", 0]
      }
    },

    "16": {
      "class_type": "KSamplerSelect",
      "inputs": {
        "sampler_name": "euler"
      }
    },

    "25": {
      "class_type": "RandomNoise",
      "inputs": {
        "noise_seed": %SEED%,
        "noise_mode": "randomize"
      }
    },

    "12": {
      "class_type": "UNETLoader",
      "inputs": {
        "unet_name": "flux2_dev_fp8mixed.safetensors",
        "weight_dtype": "default"
      }
    },

    "38": {
      "class_type": "CLIPLoader",
      "inputs": {
        "clip_name": "mistral_3_small_flux2_fp8.safetensors",
        "type": "flux2",
        "device": "default"
      }
    },

    "10": {
      "class_type": "VAELoader",
      "inputs": {
        "vae_name": "flux2-vae.safetensors"
      }
    },

    "26": {
      "class_type": "FluxGuidance",
      "inputs": {
        "conditioning": ["6", 0],
        "guidance": 4
      }
    },

    "13": {
      "class_type": "SamplerCustomAdvanced",
      "inputs": {
        "noise": ["25", 0],
        "guider": ["22", 0],
        "sampler": ["16", 0],
        "sigmas": ["48", 0],
        "latent_image": ["47", 0]
      }
    },

    "8": {
      "class_type": "VAEDecode",
      "inputs": {
        "samples": ["13", 0],
        "vae": ["10", 0]
      }
    },

    "9": {
      "class_type": "SaveImage",
      "inputs": {
        "images": ["8", 0],
        "filename_prefix": "Flux2"
      }
    },

    "6": {
      "class_type": "CLIPTextEncode",
      "inputs": {
        "clip": ["38", 0],
        "text": "%POSITIVE_PROMPT%"
      }
    },

    "47": {
      "class_type": "EmptyFlux2LatentImage",
      "inputs": {
        "width": 832,
        "height": 1248,
        "batch_size": 1
      }
    }
}

""";
}
