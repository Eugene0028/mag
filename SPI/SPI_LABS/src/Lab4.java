import qubit.Matrix;
import qubit.QuantumRegister;

public class Lab4 {

    /**
     * Удобный хелпер: строим таблицу булевой функции f(x1, x2) по четырём значениям f(0,0)..f(1,1).
     * Порядок: f00, f01, f10, f11.
     */
    private static boolean[][] truthTable(boolean f00, boolean f01, boolean f10, boolean f11) {
        return new boolean[][]{
                {f00, f01, f10, f11}
        };
    }

    /**
     * Моделирование «квантового параллелизма»: входы в состоянии |+⟩|+⟩, вспомогательный кубит |1⟩,
     * единичный оператор f применяется сразу ко всем четырём наборам входных переменных.
     */
    private static void demonstrateParallelism(String name, boolean[][] table) {
        System.out.println("\n=== Эффект квантового параллелизма для функции " + name + " ===");

        // |001⟩: x1=0, x2=0, y=1
        QuantumRegister register = QuantumRegister.fromBitString("001");

        System.out.println("Начальное состояние регистра (перед Адамаром):");
        register.printStateNotation();

        // Применяем H к трём кубитам: получаем суперпозицию по x1,x2 и |-> по y
        register.applyHadamardFirstN(3);
        System.out.println("Состояние после применения преобразования Адамара ко всем трём кубитам:");
        register.printStateNotation();

        // Оракул f(x1,x2)
        Matrix oracle = Matrix.fromBooleanTable(2, 1, table);
        register.applyFunctionMatrix(oracle.data());

        System.out.println("Состояние после применения унитарного оператора, реализующего f(x1, x2):");
        register.printStateNotation();
    }

    /**
     * Моделирование алгоритма Дойча–Йожи для конкретной булевой функции.
     * После применения H–f–H к первым двум кубитам измеряем их и смотрим, получился ли результат 00.
     */
    private static void demonstrateDeutschJozsa(String name, boolean[][] table) {
        System.out.println("\n=== Алгоритм Дойча–Йожи для функции " + name + " ===");

        QuantumRegister register = QuantumRegister.fromBitString("001");

        // Подготовка входов в |+⟩, выхода в |−⟩
        register.applyHadamardFirstN(3);

        // Оракул f
        Matrix oracle = Matrix.fromBooleanTable(2, 1, table);
        register.applyFunctionMatrix(oracle.data());

        // H только на двух входных кубитах
        register.applyHadamardFirstN(2);

        System.out.println("Состояние регистра перед измерением (скобочная нотация):");
        register.printStateNotation();

        int measuredState = register.measure();
        int twoHighBits = measuredState >> 1;
        System.out.println("Результат измерения (два старших бита): " + twoHighBits);

        if (twoHighBits == 0) {
            System.out.println("Алгоритм Дойча–Йожи говорит: функция КОНСТАНТНАЯ.");
        } else {
            System.out.println("Алгоритм Дойча–Йожи говорит: функция СБАЛАНСИРОВАННАЯ.");
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Лабораторная работа 4: квантовый параллелизм и задача Дойча–Йожи ===");

        // 1. Эффект квантового параллелизма для разных булевых функций f(x1, x2)
        boolean[][] constZero = truthTable(false, false, false, false);
        boolean[][] constOne = truthTable(true, true, true, true);
        boolean[][] balancedXor = truthTable(false, true, true, false);   // XOR
        boolean[][] balancedAlt = truthTable(false, true, false, true);   // 0,1,0,1

        demonstrateParallelism("f_const0(x1,x2)=0", constZero);
        demonstrateParallelism("f_const1(x1,x2)=1", constOne);
        demonstrateParallelism("f_xor(x1,x2)=x1 XOR x2", balancedXor);
        demonstrateParallelism("f_alt(x1,x2) = {0,1,0,1}", balancedAlt);

        // 2. Алгоритм Дойча–Йожи: константные и сбалансированные функции
        demonstrateDeutschJozsa("f_const0(x1,x2)=0 (константная)", constZero);
        demonstrateDeutschJozsa("f_xor(x1,x2)=x1 XOR x2 (сбалансированная)", balancedXor);

        // 3. Функция, которая НИ константная, НИ сбалансированная (например, 0,0,0,1)
        boolean[][] neitherConstNorBalanced = truthTable(false, false, false, true);
        demonstrateDeutschJozsa("f_mixed(x1,x2) = {0,0,0,1} (ни константная, ни сбалансированная)", neitherConstNorBalanced);
    }
}

