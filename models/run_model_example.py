import numpy as np
import tensorflow as tf

# Location of tflite model file
model_path = "cnn_rightpocket_leftwrist_rightankle_chest.tflite"

# Load TFLite model and allocate tensors.
interpreter = tf.lite.Interpreter(model_path=model_path)

# Get input and output tensors.
input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

# Allocate tensors
interpreter.allocate_tensors()

# Get input shape
input_shape = input_details[0]['shape']
# Generate random input
input_data = np.random.rand(*input_shape).astype(np.float32)

# Run model
interpreter.set_tensor(input_details[0]['index'], input_data)
interpreter.invoke()

# Get results
output_data = interpreter.get_tensor(output_details[0]['index'])
# Compute probabilities
probabilities = tf.nn.softmax(output_data)
# Compute predictions 
predictions = np.argmax(probabilities, axis=-1)
print("Classe predetta:", predictions)  # Classe più probabile per ogni batch