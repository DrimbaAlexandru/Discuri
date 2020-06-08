package Utils.Exceptions;

import static Utils.Exceptions.DataSourceExceptionCause.GENERIC_ERROR;

/**
 * Created by Alex on 08.12.2017.
 */
public class DataSourceException extends Exception
{
    private DataSourceExceptionCause DSEcause;

    public DataSourceException( DataSourceExceptionCause _cause )
    {
        super();
        DSEcause = _cause;
    }

    public DataSourceException( String msg, DataSourceExceptionCause _cause )
    {
        super( msg );
        DSEcause = _cause;
    }

    public DataSourceException( String msg )
    {
        super( msg );
        DSEcause = GENERIC_ERROR;
    }

    public DataSourceException()
    {
        super();
        DSEcause = GENERIC_ERROR;
    }

    public DataSourceExceptionCause getDSEcause()
    {
        return DSEcause;
    }
}
