package com.proxy.gate.utils;

public class Either<E, S> {

  private final E error;
  private final S success;

  private Either(E error, S success) {
    this.error = error;
    this.success = success;
  }

  public boolean isSuccess() {
    return !this.isError();
  }

  public boolean isError() {
    return this.success == null;
  }

  public E getError() {
    return this.error;
  }

  public S getSuccess() {
    return this.success;
  }

  public static <E, S> Either<E, S> success(S e) {
    return new Either(null, e);
  }

  public static <E, S> Either<E, S> error(E e) {
    return new Either(e, null);
  }

}
