package qubit;

/**
 * Булева матрица квантового преобразования F, работающая над регистрами
 * с {@code inputBits} входными и {@code outputBits} выходными кубитами.
 * <p>
 * Матрица имеет размер {@code N × N}, где {@code N = 2^(inputBits + outputBits)},
 * и может быть построена по таблице значений булевой функции.
 */
public final class Matrix {

    private final int inputBits;
    private final int outputBits;
    private final boolean[][] data; // размер N x N, где N = 2^(inputBits + outputBits)

    private Matrix(int inputBits, int outputBits, boolean[][] data) {
        this.inputBits = inputBits;
        this.outputBits = outputBits;
        this.data = data;
    }

    /**
     * Строит матрицу преобразования по таблице значений булевой функции.
     *
     * @param inputBits  количество входных бит (аргумент функции)
     * @param outputBits количество выходных бит (значение функции)
     * @param table      {@code table[outputIndex][inputState] = f_output_bit}
     */
    public static Matrix fromBooleanTable(int inputBits, int outputBits, boolean[][] table) {
        int size = 1 << (inputBits + outputBits);
        boolean[][] m = new boolean[size][size];

        for (int inputState = 0; inputState < size; ++inputState) {
            int inputBitsPart = inputState >> outputBits;
            int outputBitsPart = inputState & ((1 << outputBits) - 1);

            int newOutputBits = 0;
            for (int outputIdx = 0; outputIdx < outputBits; ++outputIdx) {
                boolean tableBit = table[outputIdx][inputBitsPart];
                boolean outputBit = ((outputBitsPart >> outputIdx) & 1) != 0;
                boolean newBit = outputBit ^ tableBit;
                if (newBit) {
                    newOutputBits |= (1 << outputIdx);
                }
            }

            int newState = (inputBitsPart << outputBits) | newOutputBits;
            m[newState][inputState] = true;
        }

        return new Matrix(inputBits, outputBits, m);
    }

    public boolean[][] data() {
        return data;
    }

    public boolean isUnitary() {
        int size = 1 << (inputBits + outputBits);

        for (int i = 0; i < size; ++i) {
            for (int j = 0; j < size; ++j) {
                boolean productIJ = false;
                for (int k = 0; k < size; ++k) {
                    if (data[k][i] && data[k][j]) {
                        productIJ = true;
                        break;
                    }
                }

                boolean expected = (i == j);
                if (productIJ != expected) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (boolean[] row : data) {
            for (boolean cell : row) {
                sb.append(cell ? "1 " : "0 ");
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }
}


