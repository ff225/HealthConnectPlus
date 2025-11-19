#!/usr/bin/env python3
"""
Test FOG - Valutazione modelli HAR su computer
Genera dati sintetici e testa i 3 modelli quantizzati con 50 iterazioni.
"""

import numpy as np
import tensorflow as tf
import time
import os
from pathlib import Path

# ============================================================================
# CONFIGURAZIONE
# ============================================================================

ACTIVITIES = ['Walking', 'Running', 'Standing', 'Sitting', 'Upstairs', 'Downstairs']
NUM_CLASSES = len(ACTIVITIES)
ACTIVITY_MAP = {activity: i for i, activity in enumerate(ACTIVITIES)}

# Parametri sensori
ACC_RANGE = 16  # ±16g
GYRO_RANGE = 2000  # ±2000°/s
MAG_RANGE = 4900  # ±4900µT

# Parametri finestre
FREQ_HIGH = 25  # Hz
FREQ_LOW = 13   # Hz
WINDOW_SIZE_HIGH = 64  # samples @ 25Hz
WINDOW_SIZE_LOW = 32   # samples @ 13Hz
OVERLAP = 0.5

# Test configuration
TEST_SESSIONS_PER_ACTIVITY = 2  # sessioni di test (piccolo dataset)
SESSION_DURATION = 60  # secondi
N_ITERATIONS = 50  # numero di iterazioni per test affidabile

# Modelli da testare
MODELS = [
    {
        'name': 'A_AccGyroMag_25Hz',
        'path': 'saved_model/A_AccGyroMag_25Hz_quantized.tflite',
        'sensors': ['acc', 'gyro', 'mag'],
        'freq': FREQ_HIGH,
        'window_size': WINDOW_SIZE_HIGH
    },
    {
        'name': 'B_AccGyroMag_13Hz',
        'path': 'saved_model/B_AccGyroMag_13Hz_quantized.tflite',
        'sensors': ['acc', 'gyro', 'mag'],
        'freq': FREQ_LOW,
        'window_size': WINDOW_SIZE_LOW
    },
    {
        'name': 'C_Acc_25Hz',
        'path': 'saved_model/C_Acc_25Hz_quantized.tflite',
        'sensors': ['acc'],
        'freq': FREQ_HIGH,
        'window_size': WINDOW_SIZE_HIGH
    }
]

# Seed per riproducibilità
np.random.seed(42)

# ============================================================================
# FUNZIONI DI GENERAZIONE DATI SINTETICI
# ============================================================================

def generate_sensor_signal(activity, sensor_type, duration, freq, session_seed=None):
    """Genera segnale sintetico REALISTICO per un sensore specifico."""
    if session_seed is not None:
        np.random.seed(session_seed)

    n_samples = int(duration * freq)
    t = np.linspace(0, duration, n_samples)

    # Parametri BASE per attività
    base_params = {
        'Walking': {
            'acc': {'mean': [0.2, 9.8, 0.3], 'amp': [2.0, 3.0, 1.5], 'freq': [2, 2, 2]},
            'gyro': {'mean': [0, 0, 0], 'amp': [50, 30, 80], 'freq': [2, 2, 2]},
            'mag': {'mean': [20, -10, 40], 'amp': [5, 5, 5], 'freq': [1, 1, 1]}
        },
        'Running': {
            'acc': {'mean': [0.5, 9.8, 0.5], 'amp': [5.0, 7.0, 4.0], 'freq': [3.5, 3.5, 3.5]},
            'gyro': {'mean': [0, 0, 0], 'amp': [150, 100, 200], 'freq': [3.5, 3.5, 3.5]},
            'mag': {'mean': [20, -10, 40], 'amp': [8, 8, 8], 'freq': [1.5, 1.5, 1.5]}
        },
        'Standing': {
            'acc': {'mean': [0, 9.8, 0], 'amp': [0.3, 0.3, 0.3], 'freq': [0.5, 0.5, 0.5]},
            'gyro': {'mean': [0, 0, 0], 'amp': [10, 10, 10], 'freq': [0.3, 0.3, 0.3]},
            'mag': {'mean': [20, -10, 40], 'amp': [3, 3, 3], 'freq': [0.2, 0.2, 0.2]}
        },
        'Sitting': {
            'acc': {'mean': [0, 9.8, 0], 'amp': [0.2, 0.2, 0.2], 'freq': [0.3, 0.3, 0.3]},
            'gyro': {'mean': [0, 0, 0], 'amp': [5, 5, 5], 'freq': [0.2, 0.2, 0.2]},
            'mag': {'mean': [20, -10, 40], 'amp': [2, 2, 2], 'freq': [0.1, 0.1, 0.1]}
        },
        'Upstairs': {
            'acc': {'mean': [0.3, 10.5, 0.2], 'amp': [3.0, 4.5, 2.0], 'freq': [1.3, 1.3, 1.3]},
            'gyro': {'mean': [0, 0, 0], 'amp': [100, 80, 120], 'freq': [1.3, 1.3, 1.3]},
            'mag': {'mean': [20, -10, 40], 'amp': [6, 6, 6], 'freq': [1, 1, 1]}
        },
        'Downstairs': {
            'acc': {'mean': [0.2, 9.2, 0.3], 'amp': [3.5, 5.0, 2.5], 'freq': [1.4, 1.4, 1.4]},
            'gyro': {'mean': [0, 0, 0], 'amp': [120, 90, 140], 'freq': [1.4, 1.4, 1.4]},
            'mag': {'mean': [20, -10, 40], 'amp': [7, 7, 7], 'freq': [1, 1, 1]}
        }
    }

    p = base_params[activity][sensor_type]

    # Variabilità inter-sessione
    mean_variation = [np.random.normal(0, 0.3) for _ in range(3)]
    amp_variation = [np.random.uniform(0.7, 1.3) for _ in range(3)]
    freq_variation = [np.random.uniform(0.85, 1.15) for _ in range(3)]

    signal_data = np.zeros((n_samples, 3))

    for axis in range(3):
        mean_val = p['mean'][axis] + mean_variation[axis]
        amp_val = p['amp'][axis] * amp_variation[axis]
        freq_val = p['freq'][axis] * freq_variation[axis]

        # Componente principale
        phase = np.random.uniform(0, 2*np.pi)
        main_component = mean_val + amp_val * np.sin(2 * np.pi * freq_val * t + phase)

        # Armoniche
        harmonic1 = (amp_val * np.random.uniform(0.2, 0.4)) * np.sin(2 * np.pi * freq_val * 2 * t + np.random.uniform(0, 2*np.pi))
        harmonic2 = (amp_val * np.random.uniform(0.1, 0.2)) * np.sin(2 * np.pi * freq_val * 3 * t + np.random.uniform(0, 2*np.pi))

        # Rumore
        noise_level = amp_val * 0.35
        noise = np.random.normal(0, noise_level, n_samples)

        # Drift
        drift = np.random.uniform(-0.5, 0.5) * np.linspace(0, 1, n_samples)

        # Spike
        n_spikes = np.random.randint(0, 5)
        spike_signal = np.zeros(n_samples)
        for _ in range(n_spikes):
            spike_pos = np.random.randint(0, n_samples)
            spike_signal[spike_pos] = np.random.normal(0, amp_val * 2)

        # Variabilità intra-sessione
        speed_modulation = 1 + 0.2 * np.sin(2 * np.pi * 0.1 * t)

        signal_data[:, axis] = (main_component + harmonic1 + harmonic2) * speed_modulation + noise + drift + spike_signal

    return signal_data


def create_windows(data, window_size, overlap=0.5):
    """Crea finestre sliding da segnale continuo."""
    step = int(window_size * (1 - overlap))
    n_windows = (len(data) - window_size) // step + 1

    windows = []
    for i in range(n_windows):
        start = i * step
        end = start + window_size
        if end <= len(data):
            windows.append(data[start:end])

    return np.array(windows)


def generate_test_dataset(freq, window_size, base_seed=1000):
    """Genera dataset di test per una frequenza specifica."""
    X_acc_list, X_gyro_list, X_mag_list, y_list = [], [], [], []

    for activity_idx, activity in enumerate(ACTIVITIES):
        for session_id in range(TEST_SESSIONS_PER_ACTIVITY):
            session_seed = base_seed + activity_idx * 1000 + session_id

            # Genera segnali
            acc_signal = generate_sensor_signal(activity, 'acc', SESSION_DURATION, freq, session_seed)
            gyro_signal = generate_sensor_signal(activity, 'gyro', SESSION_DURATION, freq, session_seed + 1)
            mag_signal = generate_sensor_signal(activity, 'mag', SESSION_DURATION, freq, session_seed + 2)

            # Crea finestre
            acc_windows = create_windows(acc_signal, window_size, OVERLAP)
            gyro_windows = create_windows(gyro_signal, window_size, OVERLAP)
            mag_windows = create_windows(mag_signal, window_size, OVERLAP)

            n_windows = len(acc_windows)

            X_acc_list.append(acc_windows)
            X_gyro_list.append(gyro_windows)
            X_mag_list.append(mag_windows)
            y_list.append(np.full(n_windows, ACTIVITY_MAP[activity]))

    X_acc = np.vstack(X_acc_list)
    X_gyro = np.vstack(X_gyro_list)
    X_mag = np.vstack(X_mag_list)
    y = np.concatenate(y_list)

    return X_acc, X_gyro, X_mag, y


def normalize_data(X, mean=None, std=None):
    """Normalizza dati."""
    if mean is None:
        mean = X.mean(axis=(0, 1), keepdims=True)
        std = X.std(axis=(0, 1), keepdims=True)

    X_norm = (X - mean) / (std + 1e-8)
    return X_norm, mean, std


def prepare_data_for_sensors(X_acc, X_gyro, X_mag, sensors):
    """Concatena i sensori richiesti."""
    data_list = []
    if 'acc' in sensors:
        data_list.append(X_acc)
    if 'gyro' in sensors:
        data_list.append(X_gyro)
    if 'mag' in sensors:
        data_list.append(X_mag)

    return np.concatenate(data_list, axis=2)


# ============================================================================
# FUNZIONI DI TEST
# ============================================================================

def test_tflite_model(model_path, X_test, y_test, n_iterations=50):
    """
    Testa modello TFLite con multiple iterazioni.

    Returns:
        dict con accuracy, f1_score, inference_times
    """
    # Carica modello
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    n_samples = len(X_test)

    # Storage per risultati
    all_accuracies = []
    all_inference_times = []

    print(f"  Esecuzione {n_iterations} iterazioni su {n_samples} samples...")

    for iteration in range(n_iterations):
        correct = 0
        iteration_times = []

        for i in range(n_samples):
            input_data = X_test[i:i+1].astype(np.float32)

            # Inferenza con timing
            start = time.time()
            interpreter.set_tensor(input_details[0]['index'], input_data)
            interpreter.invoke()
            output = interpreter.get_tensor(output_details[0]['index'])
            elapsed = (time.time() - start) * 1000  # ms

            iteration_times.append(elapsed)

            # Predizione
            pred_class = np.argmax(output[0])
            if pred_class == y_test[i]:
                correct += 1

        # Calcola accuracy per questa iterazione
        accuracy = correct / n_samples
        all_accuracies.append(accuracy)
        all_inference_times.extend(iteration_times)

        # Progress
        if (iteration + 1) % 10 == 0:
            print(f"    Iterazione {iteration + 1}/{n_iterations} completata")

    # Calcola statistiche
    results = {
        'accuracy_mean': np.mean(all_accuracies),
        'accuracy_std': np.std(all_accuracies),
        'accuracy_min': np.min(all_accuracies),
        'accuracy_max': np.max(all_accuracies),
        'inference_time_mean': np.mean(all_inference_times),
        'inference_time_std': np.std(all_inference_times),
        'inference_time_min': np.min(all_inference_times),
        'inference_time_max': np.max(all_inference_times),
        'inference_time_median': np.median(all_inference_times),
        'total_inferences': len(all_inference_times)
    }

    return results


# ============================================================================
# MAIN
# ============================================================================

def main():
    print("=" * 80)
    print("TEST FOG - VALUTAZIONE MODELLI HAR")
    print("=" * 80)
    print(f"Configurazione:")
    print(f"  - Test sessions per activity: {TEST_SESSIONS_PER_ACTIVITY}")
    print(f"  - Iterazioni per modello: {N_ITERATIONS}")
    print(f"  - Overlap finestre: {OVERLAP*100}%")
    print("=" * 80)

    # Genera test sets
    print("\n[1/4] Generazione test data @ 25Hz...")
    X_acc_25, X_gyro_25, X_mag_25, y_25 = generate_test_dataset(FREQ_HIGH, WINDOW_SIZE_HIGH, base_seed=5000)
    print(f"      Dataset 25Hz: {X_acc_25.shape[0]} samples")

    print("\n[2/4] Generazione test data @ 13Hz...")
    X_acc_13, X_gyro_13, X_mag_13, y_13 = generate_test_dataset(FREQ_LOW, WINDOW_SIZE_LOW, base_seed=6000)
    print(f"      Dataset 13Hz: {X_acc_13.shape[0]} samples")

    # Storage per risultati
    all_results = []

    # Test ogni modello
    print("\n[3/4] Testing modelli...")
    for idx, model_config in enumerate(MODELS):
        print(f"\n{'='*80}")
        print(f"Modello {idx+1}/3: {model_config['name']}")
        print(f"{'='*80}")

        # Check se modello esiste
        if not os.path.exists(model_config['path']):
            print(f"  ⚠️  ERRORE: Modello non trovato in {model_config['path']}")
            continue

        # Seleziona dataset corretto
        if model_config['freq'] == FREQ_HIGH:
            X_acc, X_gyro, X_mag, y = X_acc_25, X_gyro_25, X_mag_25, y_25
        else:
            X_acc, X_gyro, X_mag, y = X_acc_13, X_gyro_13, X_mag_13, y_13

        # Prepara dati
        X = prepare_data_for_sensors(X_acc, X_gyro, X_mag, model_config['sensors'])
        X_norm, _, _ = normalize_data(X)

        print(f"  Input shape: {X_norm.shape}")
        print(f"  Sensori: {', '.join(model_config['sensors'])}")
        print(f"  Frequenza: {model_config['freq']}Hz")

        # Test modello
        results = test_tflite_model(model_config['path'], X_norm, y, N_ITERATIONS)
        results['model_name'] = model_config['name']
        all_results.append(results)

        # Stampa risultati
        print(f"\n  Risultati:")
        print(f"    Accuracy:        {results['accuracy_mean']:.4f} ± {results['accuracy_std']:.4f}")
        print(f"                     (min: {results['accuracy_min']:.4f}, max: {results['accuracy_max']:.4f})")
        print(f"    Inference time:  {results['inference_time_mean']:.4f} ± {results['inference_time_std']:.4f} ms")
        print(f"                     (min: {results['inference_time_min']:.4f}, max: {results['inference_time_max']:.4f}, median: {results['inference_time_median']:.4f})")
        print(f"    Total inferences: {results['total_inferences']}")

    # Tabella comparativa
    print(f"\n[4/4] Riepilogo Comparativo")
    print("=" * 80)
    print(f"{'Modello':<25} {'Accuracy':<20} {'Inference Time (ms)':<30}")
    print("-" * 80)
    for r in all_results:
        acc_str = f"{r['accuracy_mean']:.4f} ± {r['accuracy_std']:.4f}"
        time_str = f"{r['inference_time_mean']:.4f} ± {r['inference_time_std']:.4f}"
        print(f"{r['model_name']:<25} {acc_str:<20} {time_str:<30}")
    print("=" * 80)

    # Salva CSV
    print("\n[5/4] Salvataggio risultati...")
    import csv
    output_file = 'fog_results.csv'
    with open(output_file, 'w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=all_results[0].keys())
        writer.writeheader()
        writer.writerows(all_results)
    print(f"  ✓ Risultati salvati in: {output_file}")

    print("\n" + "=" * 80)
    print("✅ TEST COMPLETATO!")
    print("=" * 80)


if __name__ == '__main__':
    main()
