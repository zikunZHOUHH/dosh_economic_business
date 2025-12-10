package com.example.dosh.model.dto.intent;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class IntentResponse {
    private String intent;
    private double confidence;
}
