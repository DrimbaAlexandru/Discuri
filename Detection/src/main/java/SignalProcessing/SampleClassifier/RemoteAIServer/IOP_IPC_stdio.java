package SignalProcessing.SampleClassifier.RemoteAIServer;

import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class IOP_IPC_stdio
{
    //----------------------------------------------
    // Local constants
    //
    // Special characters
    //----------------------------------------------
    private static final short DLE_BYTE = 0x10;
    private static final short STX_BYTE = 0x02;
    private static final short ETX_BYTE = 0x03;

    //----------------------------------------------
    // Parse states
    //----------------------------------------------
    private enum iop_parse_state_type
    {
        iop_PARSE_STATE_STX,
        iop_PARSE_STATE_DATA,
        iop_PARSE_STATE_DLE,
        iop_PARSE_STATE_ETX
    };

    private enum iop_parse_status_type
    {
        iop_PARSE_STATUS_CONTINUE,
        iop_PARSE_STATUS_ACK,
        iop_PARSE_STATUS_ERROR,
    };

    //----------------------------------------------
    // Other constants
    //----------------------------------------------
    public static final int IOP_MSG_MAX_DATA_LENGTH = 65535;
    private static final int IOP_MSG_MAX_LENGTH = IOP_MSG_MAX_DATA_LENGTH * 2 + 4 + 4;

    /******************************************
    * Local variables
    ******************************************/
    private iop_parse_state_type parse_state = iop_parse_state_type.iop_PARSE_STATE_ETX;
    private iop_parse_status_type parse_status = iop_parse_status_type.iop_PARSE_STATUS_CONTINUE;

    private byte[] rx_buffer = new byte[ IOP_MSG_MAX_LENGTH ];
    private int rx_bytes_cnt;
    private int parse_index = 0;

    private byte[] decoded_buffer = new byte[ IOP_MSG_MAX_DATA_LENGTH ];
    private int decoded_bytes = 0;

    private byte[] tx_buffer = new byte[ IOP_MSG_MAX_LENGTH ];

    private Process proc = null;

    /******************************************
    * Procedures
    *******************************************/
    public IOP_IPC_stdio( String start_command ) throws IOException
    {
        try
        {
            System.out.println( "Starting python script" );
            proc = new ProcessBuilder( start_command ).start();
            System.out.println( "Python script started" );
        }
        catch( IOException e )
        {
            proc = null;
            throw e;
        }
    }


    public Process getProcess()
    {
        return proc;
    }

    public void IOP_get_bytes() throws IOException
    {
        InputStream in = proc.getInputStream();
        int bytes_read;

        try
        {
            bytes_read = in.read( rx_buffer, rx_bytes_cnt, rx_buffer.length - rx_bytes_cnt );
            if( bytes_read < 0 )
            {
                throw new IOException( "Input stream terminated" );
            }
            rx_bytes_cnt += bytes_read;
        }
        catch( IOException e )
        {
            if( proc.isAlive() )
            {
                proc.destroy();
            }
            proc = null;
            throw e;
        }
    }


    private Byte iop_parse_byte()
    {
        byte parsed_byte = rx_buffer[ parse_index ];

        switch( parse_state )
        {
            case iop_PARSE_STATE_ETX:
                // Expect the beginning of a new frame ( DLE )
                if( parsed_byte == DLE_BYTE )
                {
                    parse_state = iop_parse_state_type.iop_PARSE_STATE_STX;
                    parse_status = iop_parse_status_type.iop_PARSE_STATUS_CONTINUE;
                }
                else
                {
                    parse_state = iop_parse_state_type.iop_PARSE_STATE_ETX;
                    parse_status = iop_parse_status_type.iop_PARSE_STATUS_ERROR;
                }
                break;

            case iop_PARSE_STATE_STX:
                // Expect the STX byte
                if( parsed_byte == STX_BYTE )
                {
                    parse_state = iop_parse_state_type.iop_PARSE_STATE_DATA;
                }
                else
                {
                    parse_state = iop_parse_state_type.iop_PARSE_STATE_ETX;
                    parse_status = iop_parse_status_type.iop_PARSE_STATUS_ERROR;
                }
                break;

            case iop_PARSE_STATE_DATA:
                // DLE bytes are to be treated specially
                if( parsed_byte == DLE_BYTE )
                {
                    parse_state = iop_parse_state_type.iop_PARSE_STATE_DLE;
                }
                else
                {
                    return parsed_byte;
                }
                break;

            case iop_PARSE_STATE_DLE:
                // Process DLE stuffing and ETX bytes. Otherwise, message is invalid
                if( parsed_byte == DLE_BYTE )
                {
                    parse_state = iop_parse_state_type.iop_PARSE_STATE_DATA;
                    return parsed_byte;
                }
                else if( parsed_byte == ETX_BYTE )
                {
                    parse_state = iop_parse_state_type.iop_PARSE_STATE_ETX;
                    parse_status = iop_parse_status_type.iop_PARSE_STATUS_ACK;
                }
                else
                {
                    parse_state = iop_parse_state_type.iop_PARSE_STATE_ETX;
                    parse_status = iop_parse_status_type.iop_PARSE_STATUS_ERROR;
                }
                break;

            default:
                // Unexpected state
                parse_state = iop_parse_state_type.iop_PARSE_STATE_ETX;
                parse_status = iop_parse_status_type.iop_PARSE_STATUS_ERROR;
                break;
        }

        return null;
    }


    public IOP_msg_struct_type IOP_get_frame()
    {
        IOP_msg_struct_type ret_val = null;
        Byte parsed_byte;

        while( ( parse_index < rx_bytes_cnt ) && ( ret_val == null ) )
        {
            parsed_byte = this.iop_parse_byte();

            if( parsed_byte != null )
            {
                decoded_buffer[ decoded_bytes ] = parsed_byte;
                decoded_bytes++;
            }

            if( ( parse_state == iop_parse_state_type.iop_PARSE_STATE_ETX ) && ( parse_status != iop_parse_status_type.iop_PARSE_STATUS_CONTINUE ) )
            {
                if( parse_status == iop_parse_status_type.iop_PARSE_STATUS_ACK )
                {
                    if( decoded_bytes < 4 )
                    {
                        System.out.println( "Invalid frame" );
                    }
                    else
                    {
                        ByteBuffer byteBuffer = ByteBuffer.wrap( decoded_buffer ).order( ByteOrder.LITTLE_ENDIAN );
                        byteBuffer.rewind();

                        ret_val = new IOP_msg_struct_type();
                        ret_val.ID = ( short )( byteBuffer.get() & 0xFF );
                        ret_val.subID = ( short )( byteBuffer.get() & 0xFF );
                        ret_val.size = ( int )( byteBuffer.getShort() & 0xFFFF );
                        ret_val.data = Arrays.copyOfRange( decoded_buffer, byteBuffer.position(), decoded_bytes );

                        if( ret_val.data.length != ret_val.size )
                        {
                            System.out.println( "Message sizes not matching" );
                            ret_val = null;
                        }
                    }
                }

                if( ( parse_state == iop_parse_state_type.iop_PARSE_STATE_ETX ) && ( parse_status == iop_parse_status_type.iop_PARSE_STATUS_ERROR ) )
                {
                    System.out.println( "Parse error" );
                }

                //Discard the processed bytes _rx_buffer = _rx_buffer[ parse_index + 1:]
                rx_bytes_cnt -= parse_index;
                System.arraycopy( rx_buffer, parse_index, rx_buffer, 0, rx_bytes_cnt );
                parse_index = 0;
                parse_status = iop_parse_status_type.iop_PARSE_STATUS_CONTINUE;
                decoded_bytes = 0;
            }
            else
            {
                parse_index++;
            }
        }
        return ret_val;
    }

    public int IOP_put_bytes( byte[] raw_bytes, int length ) throws IOException
    {
        proc.getOutputStream().write( raw_bytes, 0, length );

        return length;
    }

    public int IOP_put_frame( IOP_msg_struct_type msg ) throws IOException
    {
        ByteBuffer out = ByteBuffer.wrap( tx_buffer ).order( ByteOrder.LITTLE_ENDIAN );

        out.put( ( byte )DLE_BYTE );
        out.put( ( byte )STX_BYTE );
        out.put( ( byte )( msg.ID & 0xFF ) );
        out.put( ( byte )( msg.subID & 0xFF ) );
        out.putShort( ( short )( msg.size & 0xFFFF ) );

        // DLE stuffing
        for( int i = 0; i < msg.size; i++ )
        {
            if( msg.data[ i ] == DLE_BYTE )
            {
                out.put( ( byte )DLE_BYTE );
            }
            out.put( msg.data[ i ] );
        }

        out.put( ( byte )DLE_BYTE );
        out.put( ( byte )ETX_BYTE );

        return IOP_put_bytes( out.array(), out.position() );
    }
}
