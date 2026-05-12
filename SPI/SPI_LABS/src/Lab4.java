import qubit.Matrix;
import qubit.QuantumRegister;
import qubit.Qubit;

public class Lab4 {


    public static void first(){
        boolean[][] table = new boolean[][]{
                {false, false, false, true}
        };
        // A and B

        Matrix matrixToMult = Matrix.fromBooleanTable(2, 1, table);
        var register = QuantumRegister.fromQubits(new Qubit[]{
            Qubit.fromState(Qubit.QubitState.ZERO), 
            Qubit.fromState(Qubit.QubitState.PLUS), 
            Qubit.fromState(Qubit.QubitState.PLUS)});
            
        System.out.println("До преобразования");
        register.printStateNotation();
        register.applyFunctionMatrix(matrixToMult.data());

        System.out.println("После преобразования");
        register.printStateNotation();

    }


    public static void second(){
        QuantumRegister register = QuantumRegister.fromBitString("001");

        System.out.println("Состояние регистра до преобразования:");
        register.printStateNotation();

        // Применяем преобразование Адамара ко всем трём кубитам
        register.applyHadamardFirstN(3);
        System.out.println("Состояние регистра после применения Адамара к 3 кубитам:");
        register.printStateNotation();

        // Строим матрицу по таблице {0, 1, 0, 1}
        boolean[][] table = new boolean[][]{
                {false, true, false, true}
        };

        Matrix matrixToMult = Matrix.fromBooleanTable(2, 1, table);

        System.out.println("Матрица преобразования F:");
        System.out.println(matrixToMult);

        // Применяем унитарный оператор к регистру
        register.applyFunctionMatrix(matrixToMult.data());

        System.out.println("Состояние регистра после применения матрицы:");
        register.printStateNotation();

        // Снова применяем преобразование Адамара к первым двум (старшим) кубитам
        register.applyHadamardFirstN(2);

        System.out.println("Состояние регистра после применения матрицы и повторного Адамара:");
        register.printStateNotation();

        // Измеряем регистр и выводим результат сдвига вправо на 1 (деление на 2, отброс младшего бита)
        int measuredState = register.measure();
        int result = measuredState >> 1;

        System.out.println("Классический результат измерения (state >> 1): " + result);

    }

    public static void main(String[] args) {
        System.out.println("=== ПЕРВЫЙ ПУНКТ ===");
        first();
        System.out.println("=== ВТОРОЙ ПУНКТ ===");
        second();

    }
}

