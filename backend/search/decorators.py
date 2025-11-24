from django.core.exceptions import ObjectDoesNotExist
import logging
from functools import wraps
from rest_framework.response import Response
from rest_framework import status
import time
import uuid
from .exceptions import SearchExecutionError

logger = logging.getLogger(__name__)

def log_request(view_func):
    """
    Decorator to log all incoming API requests.

    Logs: HTTP method, path, user ID, and execution time.
    """
    @wraps(view_func)
    def wrapper(self, request, *args, **kwargs):

        user_id = request.user.id if request.user.is_authenticated else "anonymous"
        logger.info(
            f"[REQUEST] {request.method} {request.path} - User: {user_id}"
        )

        start_time = time.time()
        response = view_func(self, request, *args, **kwargs)
        execution_time = (time.time() - start_time) * 1000  # ms

        logger.info(
            f"[RESPONSE] {request.method} {request.path} - "
            f"Status: {response.status_code} - Time: {execution_time:.2f}ms"
        )

        return response

    return wrapper

def handle_exceptions(view_func):
    """
    Decorator for centralized exception handling.

    Handles:
    - ObjectDoesNotExist -> 404
    - ValidationError -> 400
    - PermissionDenied -> 403
    - Generic exceptions -> 500 (with logging)
    """
    @wraps(view_func)
    def wrapper(self, request, *args, **kwargs):
        try:
            return view_func(self, request, *args, **kwargs)

        except ObjectDoesNotExist:
            logger.warning(
                f"Object not found - User: {request.user.id}, "
                f"Path: {request.path}"
            )
            return Response(
                {"error": "Object not found"},
                status=status.HTTP_404_NOT_FOUND
            )

        except PermissionError:
            logger.warning(
                f"Permission denied - User: {request.user.id}, "
                f"Path: {request.path}"
            )
            return Response(
                {"error": "Permission denied"},
                status=status.HTTP_403_FORBIDDEN
            )

        except ValueError as e:
            logger.warning(f"Validation error: {str(e)}")
            return Response(
                {"error": str(e)},
                status=status.HTTP_400_BAD_REQUEST
            )

        except SearchExecutionError as e:
            logger.error(
                f"Search execution failed in {view_func.__name__}: {str(e)}",
                exc_info=True,
                extra={
                    'user_id': request.user.id,
                    'path': request.path,
                    'method': request.method
                }
            )
            return Response(
                {"error": str(e)},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )

        except Exception as e:
            # Log full error with stack trace
            logger.error(
                f"Unexpected error in {view_func.__name__}: {str(e)}",
                exc_info=True,
                extra={
                    'user_id': request.user.id,
                    'path': request.path,
                    'method': request.method
                }
            )
            # Return safe error message to client
            return Response(
                {"error": "Internal server error"},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )

    return wrapper

def validate_pagination(max_limit=50):
    """
    Decorator to validate and normalize pagination parameters.

    Validates:
    - offset >= 0
    - 1 <= limit <= max_limit

    Adds validated values to request object:
    - request.validated_offset
    - request.validated_limit
    """
    def decorator(view_func):
        @wraps(view_func)
        def wrapper(self, request, *args, **kwargs):
            try:
                offset = int(request.GET.get("offset", 0))
                limit = int(request.GET.get("limit", 50))
            except ValueError:
                return Response(
                    {"error": "offset and limit must be integers"},
                    status=status.HTTP_400_BAD_REQUEST
                )

            if offset < 0:
                return Response(
                    {"error": "offset must be >= 0"},
                    status=status.HTTP_400_BAD_REQUEST
                )

            if limit < 1 or limit > max_limit:
                return Response(
                    {"error": f"limit must be between 1 and {max_limit}"},
                    status=status.HTTP_400_BAD_REQUEST
                )

            # Add validated values to request
            request.validated_offset = offset
            request.validated_limit = limit

            return view_func(self, request, *args, **kwargs)
        return wrapper
    return decorator
