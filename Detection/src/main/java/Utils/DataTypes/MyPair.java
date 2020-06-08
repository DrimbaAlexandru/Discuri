package Utils.DataTypes;

/**
 * Created by Alex on 13.03.2018.
 */
public class MyPair< T1, T2 >
{
    private T1 left = null;
    private T2 right = null;

    public T1 getLeft()
    {
        return left;
    }

    public T2 getRight()
    {
        return right;
    }

    public void setLeft( T1 left )
    {
        this.left = left;
    }

    public void setRight( T2 right )
    {
        this.right = right;
    }

    public  MyPair( T1 left, T2 right )
    {
        setLeft( left );
        setRight( right );
    }
}
