package com.programmerdan.arionum.arionum_miner.jna;

/*
Included from de.mkammerer's library.
de.mkammerer.argon2.jna

This library is GPL3


*/
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Callback;
import com.sun.jna.ptr.PointerByReference;

/**
 * JNA bindings for Argon2.
 */
public interface Argon2Library extends Library {
    /**
     * Singleton instance.
     */
    Argon2Library INSTANCE = (Argon2Library) Native.loadLibrary("argon2", Argon2Library.class);

    /**
     * Return code if everything is okay.
     */
    int ARGON2_OK = 0;

    JnaUint32 ARGON2_DEFAULT_FLAGS = new JnaUint32(0);


    /*
     * Uses an Argon Context to generate a hash. The hash is in the "out" field.
     */
    int argon2_ctx(Argon2_Context context, Argon2_type type);


    /*
     * Uses an argon context to render a fully encoded Argon2 hash string in a
     *  standard form. This is an Arionum-specific addition to the library header.
     */
    int encode_ctx(byte[] dst, Size_t dst_len, Argon2_Context ctx, Argon2_type type);

    /*
    int argon2i_hash_encoded(const uint32_t t_cost, const uint32_t m_cost,
                         const uint32_t parallelism, const void *pwd,
                         const size_t pwdlen, const void *salt,
                         const size_t saltlen, const size_t hashlen,
                         char *encoded, const size_t encodedlen);
     */

    /**
     * Hashes a password with Argon2i, producing an encoded hash.
     *
     * @param t_cost      Number of iterations
     * @param m_cost      Sets memory usage to m_cost kibibytes
     * @param parallelism Number of threads and compute lanes
     * @param pwd         Pointer to password
     * @param pwdlen      Password size in bytes
     * @param salt        Pointer to salt
     * @param saltlen     Salt size in bytes
     * @param hashlen     Desired length of the hash in bytes
     * @param encoded     Buffer where to write the encoded hash
     * @param encodedlen  Size of the buffer (thus max size of the encoded hash)
     * @return {@link #ARGON2_OK} if successful
     */
    int argon2i_hash_encoded(JnaUint32 t_cost, JnaUint32 m_cost, JnaUint32 parallelism, byte[] pwd, Size_t pwdlen, byte[] salt, Size_t saltlen, Size_t hashlen, byte[] encoded, Size_t encodedlen);

    /**
     * Hashes a password with Argon2i, producing an encoded hash.
     *
     * @param t_cost      Number of iterations
     * @param m_cost      Sets memory usage to m_cost kibibytes
     * @param parallelism Number of threads and compute lanes
     * @param pwd         Pointer to password
     * @param pwdlen      Password size in bytes
     * @param salt        Pointer to salt
     * @param saltlen     Salt size in bytes
     * @param hash        Buffer where to write the raw hash
     * @param hashlen     Desired length of the hash in bytes
     * @return {@link #ARGON2_OK} if successful
     */
    int argon2i_hash_raw(JnaUint32 t_cost, JnaUint32 m_cost, JnaUint32 parallelism, byte[] pwd, Size_t pwdlen, byte[] salt, Size_t saltlen, byte[] hash, Size_t hashlen);

    /**
     * Verifies a password against an Argon2i encoded string.
     *
     * @param encoded String encoding parameters, salt, hash
     * @param pwd     Pointer to password
     * @param pwdlen  Password size in bytes
     * @return ARGON2_OK if successful
     */
    /*
    int argon2i_verify(const char *encoded, const void *pwd, const size_t pwdlen);
     */
    int argon2i_verify(byte[] encoded, byte[] pwd, Size_t pwdlen);


    /**
     * Returns the encoded hash length for the given input parameters.
     *
     * @param t_cost      Number of iterations.
     * @param m_cost      Memory usage in kibibytes.
     * @param parallelism Number of threads; used to compute lanes.
     * @param saltlen     Salt size in bytes.
     * @param hashlen     Hash size in bytes.
     * @param type        The argon2 type.
     * @return The encoded hash length in bytes.
     */
    Size_t argon2_encodedlen(JnaUint32 t_cost, JnaUint32 m_cost, JnaUint32 parallelism, JnaUint32 saltlen, JnaUint32 hashlen, Argon2_type type);

    /**
     * Get the associated error message for given error code.
     *
     * @param error_code Numeric error code.
     * @return The error message associated with the given error code.
     */
    String argon2_error_message(int error_code);


    public interface AllocateFunction extends Callback {
        int invoke(PointerByReference memory, Size_t byte_to_allocate);
    }
    
    public interface DeallocateFunction extends Callback {
        void invoke(Pointer memory, Size_t byte_to_allocate);
    }
 
}
