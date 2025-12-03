import qubit.Qubit;


public class Lab1 {
    public static void demonstrateMeasurement(Qubit inputQubit) {
        System.out.println("=== Демонстрация стандартного измерения кубита ===");
        System.out.println("Квантовое состояние до измерения (амплитуды и вероятности):");
        inputQubit.printState();

        int classicalMeasurementResult = Qubit.QuantumMath.measureStandard(inputQubit, true);

        System.out.println("\nКвантовое состояние после стандартного измерения:");
        inputQubit.printState();
        System.out.println("Полученный классический результат измерения (0 или 1): " + classicalMeasurementResult);
    }

    public static void demonstrateRotatedMeasurement(Qubit inputQubit) {
        System.out.println("=== Демонстрация измерения в повернутом (Адамаровом) базисе ===");
        System.out.println("Исходное квантовое состояние кубита перед преобразованием Адамара:");
        inputQubit.printState();

        int classicalMeasurementResult = Qubit.QuantumMath.measureRotated(inputQubit, true);

        System.out.println("\nСостояние кубита после измерения в повернутом базисе:");
        inputQubit.printState();
        System.out.println("Полученный классический результат измерения в повернутом базисе: " + classicalMeasurementResult);
    }


    public static void main(String[] args) {
        Qubit initialQubit = new Qubit(0.7071, -0.7071);

        System.out.println("=== Лабораторная работа 1: базовое измерение кубита ===");
        demonstrateMeasurement(initialQubit);

        System.out.println("\n\n=== Повторная демонстрация: измерение в повернутом базисе для того же кубита ===");
        demonstrateRotatedMeasurement(initialQubit);
    }
}


