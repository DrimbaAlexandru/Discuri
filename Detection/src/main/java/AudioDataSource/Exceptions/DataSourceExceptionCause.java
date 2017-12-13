package AudioDataSource.Exceptions;

/**
 * Created by Alex on 08.12.2017.
 */
public enum DataSourceExceptionCause
{
    NO_ERR,
    GENERIC_ERROR,
    NOT_ENOUGH_SPACE,
    NOT_ENOUGH_FREE_SPACE,
    SAMPLE_ALREADY_CACHED,
    SAMPLE_NOT_CACHED,
    CHANNEL_NOT_VALID,
    IO_ERROR,
    UNSUPPORTED_FILE_FORMAT,
    METHOD_NOT_SUPPORTED,
    BAD_FILE_FORMAT

}
