from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route('/predict', methods=['POST'])
def predict():
    data = request.json
    text = data.get('text', '').lower()
    
    intent = 'chat'
    confidence = 0.95
    
    if 'image' in text or 'draw' in text or 'picture' in text:
        intent = 'image_generation'
    elif 'video' in text or 'movie' in text:
        intent = 'video_generation'
        
    return jsonify({
        'intent': intent,
        'confidence': confidence
    })

if __name__ == '__main__':
    print("Starting Mock Intent Server on port 8000...")
    app.run(port=8000)
