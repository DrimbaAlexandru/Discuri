package SignalProcessing.LiniarPrediction;

/**
 * Created by Alex on 17.11.2017.
 */
public class NotEnoughSamplesException extends Exception
{
    public NotEnoughSamplesException()
    {
        super();
    }

    public NotEnoughSamplesException( String msg )
    {
        super( msg );
    }

}
