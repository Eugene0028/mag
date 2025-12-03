package qubit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Квантовый регистр фиксированного размера, представленный в виде вектора амплитуд
 * для всех базисных состояний вычислительного базиса.
 * <p>
 * Размер регистра (количество кубитов) задаётся во время выполнения,
 * всего состояний {@code 2^qubitCount}. Класс предоставляет операции
 * над регистром: применение преобразования Адамара, унитарных матриц
 * и моделирование измерения.
 */
public class QuantumRegister {

    private final int qubitCount;
    private final int stateCount;
    private double[] stateVector;

    /** Создаёт регистр из {@code qubitCount} кубитов в состоянии |00...0⟩. */
    public QuantumRegister(int qubitCount) {
        if (qubitCount <= 0) {
            throw new IllegalArgumentException("Количество кубитов должно быть положительным");
        }
        this.qubitCount = qubitCount;
        this.stateCount = 1 << qubitCount;
        this.stateVector = new double[stateCount];
        this.stateVector[0] = 1.0; // |00...0⟩
    }

    /** Создаёт регистр заданного размера и устанавливает его в указанное базисное состояние. */
    public QuantumRegister(int qubitCount, int basisStateIndex) {
        this(qubitCount);
        if (basisStateIndex >= 0 && basisStateIndex < stateCount) {
            Arrays.fill(this.stateVector, 0.0);
            this.stateVector[basisStateIndex] = 1.0;
        }
    }

    /** Создаёт регистр по битовой строке вида {@code "01010000"} (старший бит слева). */
    public static QuantumRegister fromBitString(String bitString) {
        if (bitString == null || bitString.isEmpty()) {
            throw new IllegalArgumentException("bitString не должен быть пустым");
        }
        int qubitCount = bitString.length();
        int stateCount = 1 << qubitCount;
        QuantumRegister register = new QuantumRegister(qubitCount);
        register.stateVector = new double[stateCount];
        Arrays.fill(register.stateVector, 0.0);

        int basisStateIndex = 0;
        for (int i = 0; i < bitString.length(); i++) {
            char c = bitString.charAt(i);
            basisStateIndex <<= 1;
            if (c == '1') {
                basisStateIndex |= 1;
            }
        }
        if (basisStateIndex >= 0 && basisStateIndex < stateCount) {
            register.stateVector[basisStateIndex] = 1.0;
        }
        return register;
    }

    /** Создаёт регистр как тензорное произведение одно-кубитных состояний. */
    public static QuantumRegister fromQubits(Qubit[] qubits) {
        if (qubits == null || qubits.length == 0) {
            throw new IllegalArgumentException("Массив qubits не должен быть пустым");
        }
        int qubitCount = qubits.length;
        int stateCount = 1 << qubitCount;
        QuantumRegister register = new QuantumRegister(qubitCount);
        register.stateVector = new double[stateCount];

        for (int state = 0; state < stateCount; state++) {
            double amplitude = 1.0;
            for (int qubitIndex = 0; qubitIndex < qubitCount; qubitIndex++) {
                boolean qubitValue = ((state >> qubitIndex) & 1) != 0;
                if (qubitValue) {
                    amplitude *= qubits[qubitIndex].ket();
                } else {
                    amplitude *= qubits[qubitIndex].bra();
                }
            }
            register.stateVector[state] = amplitude;
        }

        return register;
    }

    public int size() {
        return qubitCount;
    }

    public int stateCount() {
        return stateCount;
    }

    /** Применяет преобразование Адамара к одному выбранному кубиту. */
    public void applyHadamard(int targetQubitIndex) {
        if (targetQubitIndex < 0 || targetQubitIndex >= qubitCount) {
            return;
        }

        final double invSqrt2 = 1.0 / Math.sqrt(2.0);
        double[] newState = new double[stateCount];

        for (int i = 0; i < stateCount; i++) {
            int baseState = i & ~(1 << targetQubitIndex);

            double amp0 = stateVector[baseState];
            double amp1 = stateVector[baseState | (1 << targetQubitIndex)];

            newState[baseState] = invSqrt2 * amp0 + invSqrt2 * amp1;
            newState[baseState | (1 << targetQubitIndex)] = invSqrt2 * amp0 - invSqrt2 * amp1;
        }

        this.stateVector = newState;
    }

    public void applyHadamardFirstN(int n) {
        n = Math.min(n, qubitCount);
        int skip = qubitCount - n;

        for (int i = skip; i < qubitCount; ++i) {
            applyHadamard(i);
        }
    }

    public void applyHadamardToAll() {
        applyHadamardFirstN(qubitCount);
    }

    /** Моделирует измерение всего регистра в вычислительном базисе и возвращает индекс измеренного состояния. */
    public int measure() {
        double[] probabilities = new double[stateCount];
        double totalProb = 0.0;

        for (int state = 0; state < stateCount; state++) {
            probabilities[state] = stateVector[state] * stateVector[state];
            totalProb += probabilities[state];
        }

        double randomValue = Qubit.QuantumRandom.getRandom() * totalProb;
        double cumulativeProb = 0.0;

        for (int state = 0; state < stateCount; state++) {
            cumulativeProb += probabilities[state];
            if (randomValue <= cumulativeProb) {
                Arrays.fill(stateVector, 0.0);
                stateVector[state] = 1.0;
                return state;
            }
        }

        return 0;
    }

    /** Печатает состояние в виде суперпозиции {@code |ψ⟩ = Σ a_i |i⟩} в скобочной нотации. */
    public void printStateNotation() {
        System.out.print("|ψ⟩ = ");

        boolean allZeros = true;
        for (double amplitude : stateVector) {
            if (Math.abs(amplitude) > 1e-10) {
                allZeros = false;
                break;
            }
        }

        if (allZeros) {
            System.out.println("0");
            return;
        }

        boolean firstTerm = true;
        for (int state = 0; state < stateCount; state++) {
            double amplitude = stateVector[state];
            if (Math.abs(amplitude) <= 1e-10) {
                continue;
            }

            if (!firstTerm) {
                System.out.print(amplitude >= 0 ? " + " : " - ");
            } else {
                firstTerm = false;
                if (amplitude < 0) {
                    System.out.print("-");
                }
            }

            double displayAmplitude = Math.abs(amplitude);
            String ampStr = Double.toString(displayAmplitude)
                    .replaceAll("0+$", "")
                    .replaceAll("\\.$", "");

            System.out.print(ampStr + "|");

            for (int bit = qubitCount - 1; bit >= 0; bit--) {
                int bitValue = (state >> bit) & 1;
                System.out.print(bitValue);
            }
            System.out.print("⟩");
        }
        System.out.println();
    }

    /** Применяет булеву матрицу функции; {@code true} означает наличие перехода между состояниями. */
    public void applyFunctionMatrix(boolean[][] matrix) {
        if (matrix.length != stateCount || matrix[0].length != stateCount) {
            throw new IllegalArgumentException("Размер матрицы должен совпадать с размером регистра");
        }

        double[] newState = new double[stateCount];

        for (int i = 0; i < stateCount; i++) {
            double amplitude = 0.0;
            for (int j = 0; j < stateCount; j++) {
                if (matrix[i][j]) {
                    amplitude += stateVector[j];
                }
            }
            newState[i] = amplitude;
        }

        this.stateVector = newState;
    }

    /** Применяет произвольную вещественную матрицу размера {@code stateCount × stateCount}. */
    public void applyMatrix(double[][] matrix) {
        if (matrix.length != stateCount || matrix[0].length != stateCount) {
            throw new IllegalArgumentException("Размер матрицы должен совпадать с размером регистра");
        }

        double[] newState = new double[stateCount];

        for (int i = 0; i < stateCount; i++) {
            double amplitude = 0.0;
            for (int j = 0; j < stateCount; j++) {
                amplitude += matrix[i][j] * stateVector[j];
            }
            newState[i] = amplitude;
        }

        this.stateVector = newState;
    }

    public double[] getStateVector() {
        return stateVector.clone();
    }

    public double getAmplitude(int state) {
        if (state < 0 || state >= stateCount) return 0.0;
        return stateVector[state];
    }

    public double getProbability(int state) {
        double amp = getAmplitude(state);
        return amp * amp;
    }

    public void setState(int state) {
        if (state < 0 || state >= stateCount) return;
        Arrays.fill(stateVector, 0.0);
        stateVector[state] = 1.0;
    }

    public double checkNormalization() {
        double sum = 0.0;
        for (double amplitude : stateVector) {
            sum += amplitude * amplitude;
        }
        return sum;
    }

    public List<StateAmplitude> getNonZeroStates() {
        List<StateAmplitude> result = new ArrayList<>();
        for (int state = 0; state < stateCount; state++) {
            if (Math.abs(stateVector[state]) > 1e-10) {
                result.add(new StateAmplitude(state, stateVector[state]));
            }
        }
        return result;
    }

    public record StateAmplitude(int stateIndex, double amplitude) {
    }
}


