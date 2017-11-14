package SignalProcessing.LiniarPrediction;

/**
 * Created by Alex on 10.11.2017.
 */
public interface LiniarPrediction
{
    void extrapolate( double[] left, double[] center, double[] right, int length );
}
