import qubit.Matrix;
import qubit.QuantumRegister;

public class Lab3 {

    // Количество бит, необходимых для представления числа n
    private static int bitsRequired(long n) {
        if (n == 0) return 1;
        int bits = 0;
        while (n != 0) {
            bits++;
            n >>= 1;
        }
        return bits;
    }

    // Строит таблицу значений функции f(x) = a^x mod m по битам
    // (совместимо с Matrix.fromBooleanTable: table[outputBit][inputState]).
    private static boolean[][] toTable(int bits, long a, long m) {
        int columnCount = 1 << bits;
        boolean[][] table = new boolean[bits][columnCount];

        for (int inputValue = 0; inputValue < columnCount; inputValue++) {
            long result = (long) Math.pow(a, inputValue) % m;

            for (int bitIndex = 0; bitIndex < bits; bitIndex++) {
                table[bitIndex][inputValue] = ((result >> bitIndex) & 1L) != 0L;
            }
        }

        return table;
    }

    public static void main(String[] args) {
        System.out.println("=== ПУНКТ 1 ЗАДАНИЯ: построение матрицы для булевой функции двух переменных ===");

        // === ПУНКТ 1 ЗАДАНИЯ: построение матрицы для булевой функции двух переменных ===
        // Пример булевой функции f(x1, x2) с таблицей истинности:
        // x2 x1 | f
        //  0  0 | 1
        //  0  1 | 1
        //  1  0 | 0
        //  1  1 | 1
        boolean[][] booleanFunctionTable = new boolean[][]{
                {true, true, false, true}
        };

        Matrix booleanFunctionMatrix = Matrix.fromBooleanTable(2, 1, booleanFunctionTable);
        System.out.println("Матрица F для выбранной булевой функции f(x1, x2):");
        System.out.println(booleanFunctionMatrix);
        System.out.println("Полученная матрица унитарна? "
                + (booleanFunctionMatrix.isUnitary() ? "Да (унитарная)" : "Нет (НЕ унитарная)"));

        System.out.println();
        System.out.println("=== ПУНКТ 2 ЗАДАНИЯ: квантовое вычисление функции f(x) = a^x mod m ===");

        final int a = 2;
        final int m = 15;
        final int bits = bitsRequired(m);

        boolean[][] functionTable = toTable(bits, a, m);
        Matrix matrixToMultiply = Matrix.fromBooleanTable(bits, bits, functionTable);

        // Входное состояние: 5 (101) на входных битах и нули на выходных
        String initialBitString = "01010000"; // для bits = 4 имеем 2*bits = 8 кубитов
        QuantumRegister register = QuantumRegister.fromBitString(initialBitString);

        System.out.println("Исходное состояние регистра:");
        register.printStateNotation();

        register.applyFunctionMatrix(matrixToMultiply.data());

        System.out.println("Состояние регистра после применения унитарного оператора f(x) = a^x mod m:");
        register.printStateNotation();
    }
}


