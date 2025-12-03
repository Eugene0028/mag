import java.util.HashMap;
import java.util.Map;
import qubit.Qubit;



public class Lab2 {

    enum Basis {
        STANDARD,
        HADAMARD
    }

    static class QuantumMeasurer {
        public static int measure(Qubit qubit, Basis basis) {
            if (basis == Basis.STANDARD) {
                return Qubit.QuantumMath.measureStandard(qubit);
            }
            return Qubit.QuantumMath.measureRotated(qubit);
        }

        public static String getBasisName(Basis basis) {
            return (basis == Basis.STANDARD)
                    ? "Стандартный {|0⟩,|1⟩}"
                    : "Адамара {|+⟩,|-⟩}";
        }
    }

    static class QubitProtocol {
        private final Map<Qubit.QubitState, String> qubitStateDisplayNames = new HashMap<>();
        private final Map<Qubit.QubitState, Integer> qubitStateSecretBitValues = new HashMap<>();

        private static final String RESET = "\033[0m";
        private static final String RED = "\033[31m";
        private static final String GREEN = "\033[32m";

        public QubitProtocol() {
            qubitStateDisplayNames.put(Qubit.QubitState.ZERO, "|0⟩");
            qubitStateDisplayNames.put(Qubit.QubitState.ONE, "|1⟩");
            qubitStateDisplayNames.put(Qubit.QubitState.PLUS, "|+⟩");
            qubitStateDisplayNames.put(Qubit.QubitState.MINUS, "|-⟩");

            qubitStateSecretBitValues.put(Qubit.QubitState.ZERO, 0);
            qubitStateSecretBitValues.put(Qubit.QubitState.ONE, 1);
            qubitStateSecretBitValues.put(Qubit.QubitState.PLUS, 0);
            qubitStateSecretBitValues.put(Qubit.QubitState.MINUS, 1);
        }

        public Qubit.QubitState generateRandomQubitStateForAlice() {
            int randomChoiceIndex = Qubit.QuantumRandom.getRandomInt(0, 3);
            return switch (randomChoiceIndex) {
                case 1 -> Qubit.QubitState.ONE;
                case 2 -> Qubit.QubitState.PLUS;
                case 3 -> Qubit.QubitState.MINUS;
                default -> Qubit.QubitState.ZERO;
            };
        }

        public Basis chooseRandomMeasurementBasisForBob() {
            return (Qubit.QuantumRandom.getRandomInt(0, 1) == 0)
                    ? Basis.STANDARD
                    : Basis.HADAMARD;
        }

        public boolean arePreparationAndMeasurementBasesCompatible(Qubit.QubitState preparedQubitState, Basis bobMeasurementBasis) {
            if (bobMeasurementBasis == Basis.STANDARD) {
                return preparedQubitState == Qubit.QubitState.ZERO || preparedQubitState == Qubit.QubitState.ONE;
            } else {
                return preparedQubitState == Qubit.QubitState.PLUS || preparedQubitState == Qubit.QubitState.MINUS;
            }
        }

        public int getSecretBitAssociatedWithState(Qubit.QubitState preparedQubitState) {
            return qubitStateSecretBitValues.get(preparedQubitState);
        }

        public String getDisplayNameForQubitState(Qubit.QubitState preparedQubitState) {
            return qubitStateDisplayNames.get(preparedQubitState);
        }

        public boolean runProtocol() {
            // Шаг 1: пользователь A (Алиса) генерирует квантовое состояние кубита
            Qubit.QubitState alicePreparedQubitState = generateRandomQubitStateForAlice();
            Qubit alicePreparedQubit = Qubit.fromState(alicePreparedQubitState);

            System.out.println("ШАГ 1: Пользователь A (Алиса) подготавливает однофотонный кубит в одном из четырёх возможных состояний.");
            System.out.print("Квантовое состояние, выбранное Алисой: " + getDisplayNameForQubitState(alicePreparedQubitState) + " = ");
            alicePreparedQubit.printState();
            System.out.println("\nСекретный классический бит, который кодируется выбранным состоянием Алисы: "
                    + getSecretBitAssociatedWithState(alicePreparedQubitState));

            // Шаг 2: пользователь B (Боб) случайно выбирает базис и измеряет кубит
            Basis bobMeasurementBasis = chooseRandomMeasurementBasisForBob();
            int bobMeasurementResult = QuantumMeasurer.measure(alicePreparedQubit, bobMeasurementBasis);

            System.out.println("\nШАГ 2: Пользователь B (Боб) получает кубит и случайно выбирает базис измерения.");
            System.out.println("Боб выбрал следующий базис измерения: " + QuantumMeasurer.getBasisName(bobMeasurementBasis));
            System.out.println("Результат одиночного измерения, полученный Бобом (классическое значение 0 или 1): " + bobMeasurementResult);

            // Шаг 3: Боб сообщает Алисе, какой базис он использовал
            System.out.println("\nШАГ 3: Пользователь B (Боб) сообщает Алисе по классическому каналу, какой базис он использовал.");
            System.out.println("Сообщение Боба: 'Я использовал базис " + QuantumMeasurer.getBasisName(bobMeasurementBasis) + "'");

            // Шаг 4: Алиса проверяет, совпал ли её базис подготовки с базисом измерения Боба
            boolean preparationAndMeasurementBasesAreCompatible =
                    arePreparationAndMeasurementBasesCompatible(alicePreparedQubitState, bobMeasurementBasis);

            System.out.println("\nШАГ 4: Пользователь A (Алиса) сравнивает свой базис подготовки с базисом измерения Боба.");
            System.out.println("Базис Алисы (по подготовленному состоянию): " +
                    ((alicePreparedQubitState == Qubit.QubitState.ZERO || alicePreparedQubitState == Qubit.QubitState.ONE)
                            ? "Стандартный {|0⟩,|1⟩}"
                            : "Адамара {|+⟩,|-⟩}"));
            System.out.println("Базис Боба (фактически использованный при измерении): " + QuantumMeasurer.getBasisName(bobMeasurementBasis));

            if (preparationAndMeasurementBasesAreCompatible) {
                System.out.println(GREEN + "Базисы подготовки и измерения совпали. Результат измерения Боба надёжен." + RESET);
                System.out.println("\nПРОТОКОЛ УСПЕШЕН: бит может быть использован как часть общего секрета.");
                int sharedSecretBit = getSecretBitAssociatedWithState(alicePreparedQubitState);
                System.out.println("Общий секретный бит, разделяемый Алисой и Бобом: " + sharedSecretBit);
                System.out.println("Сообщение Алисы: 'OK' (фиксируем этот бит в ключе)");
                return true;
            } else {
                System.out.println(RED + "\nБазисы подготовки и измерения не совпали — результат Боба неинформативен." + RESET);
                System.out.println("Сообщение Алисы: 'ПОВТОР' (этот бит отбрасывается, протокол выполняется снова с новым кубитом)");
                return false;
            }
        }
    }

    public static void main(String[] args) {
        QubitProtocol protocol = new QubitProtocol();
        boolean success = false;

        while (!success) {
            success = protocol.runProtocol();
        }
    }
}

