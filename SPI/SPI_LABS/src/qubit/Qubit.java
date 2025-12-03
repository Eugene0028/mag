package qubit;

import java.util.Random;

public class Qubit {

    public enum QubitState {
        ZERO,
        ONE,
        PLUS,
        MINUS
    }

    private double mBra; // |0⟩
    private double mKet; // |1⟩

    /**
     * Создаёт кубит в состояния по умолчанию. Начальные амплитуды берутся равными 1,
     * после чего состояние может быть нормировано или переопределено пользователем.
     */
    public Qubit() {
        this.mBra = 1.0;
        this.mKet = 1.0;
    }

    /**
     * Создаёт кубит с заданными амплитудами |0⟩ и |1⟩
     * и автоматически нормирует состояние так, чтобы |α|² + |β|² = 1.
     */
    public Qubit(double bra, double ket) {
        this.mBra = bra;
        this.mKet = ket;
        normalize();
    }

    public static Qubit fromState(QubitState state) {
        switch (state) {
            case ZERO:
                return new Qubit(1.0, 0.0);
            case ONE:
                return new Qubit(0.0, 1.0);
            case PLUS:
                return new Qubit(1.0 / Math.sqrt(2.0), 1.0 / Math.sqrt(2.0));
            case MINUS:
                return new Qubit(1.0 / Math.sqrt(2.0), -1.0 / Math.sqrt(2.0));
            default:
                return new Qubit(1.0, 0.0);
        }
    }

    public double bra() {
        return mBra;
    }

    public double ket() {
        return mKet;
    }

    public double probabilityBra() {
        return mBra * mBra;
    }

    public double probabilityKet() {
        return mKet * mKet;
    }

    public void printState() {
        System.out.println("Состояние: " + mBra + "|0⟩ + " + mKet + "|1⟩");
        System.out.println("Вероятности: P(|0⟩) = " + probabilityBra() + ", P(|1⟩) = " + probabilityKet());
        System.out.println("Нормировка: " + "(|α|² + |β|² = " + (probabilityBra() + probabilityKet()) + ")");
    }

    public int measure() {
        return QuantumMath.measureStandard(this, false);
    }

    // Нормировка: |α|² + |β|² = 1
    private void normalize() {
        double norm = Math.sqrt(mBra * mBra + mKet * mKet);
        if (norm > 0.0) {
            mBra /= norm;
            mKet /= norm;
        }
    }

    public static final class QuantumRandom {
        private static final Random RANDOM = new Random();

        public static double getRandom() {
            return RANDOM.nextDouble(); // [0, 1)
        }

        public static int getRandomInt(int min, int max) {
            if (min > max) {
                throw new IllegalArgumentException("min must be <= max");
            }
            return min + RANDOM.nextInt(max - min + 1);
        }
    }

    public static final class QuantumMath {

        public static void applyHadamard(Qubit qubit) {
            double bra = qubit.bra();
            double ket = qubit.ket();

            qubit.mBra = (bra + ket) / Math.sqrt(2.0);
            qubit.mKet = (bra - ket) / Math.sqrt(2.0);
        }

        public static int measureStandard(Qubit qubit) {
            return measureStandard(qubit, false);
        }

        public static int measureStandard(Qubit qubit, boolean logging) {
            double random = QuantumRandom.getRandom();

            if (logging) {
                System.out.println();
                System.out.println("Процесс измерения:");
                System.out.println("P(|0⟩) = " + qubit.probabilityBra() + ", P(|1⟩) = " + qubit.probabilityKet());
                System.out.println("Случайное число: " + random);
            }

            int result;
            if (random < qubit.probabilityBra()) {
                result = 0;
                qubit.mBra = 1.0;
                qubit.mKet = 0.0;
            } else {
                result = 1;
                qubit.mBra = 0.0;
                qubit.mKet = 1.0;
            }

            if (logging) {
                System.out.println("Результат: |" + result + "⟩");
            }
            return result;
        }

        public static int measureRotated(Qubit qubit) {
            return measureRotated(qubit, false);
        }

        public static int measureRotated(Qubit qubit, boolean logging) {
            if (logging) {
                System.out.println();
                System.out.println("Поворот базиса преобразованием Адамара...");
            }

            applyHadamard(qubit);

            if (logging) {
                qubit.printState();
            }

            return measureStandard(qubit, logging);
        }
    }
}
